package com.ele.me;

import com.ele.io.ClientRequest;
import com.ele.io.ResultUnit;
import com.ele.io.RpcIOGrpc;
import com.ele.io.ServerReply;
import com.ele.raft.*;
import com.ele.util.DBConnector;
import com.ele.util.Log;
import com.ele.util.LogEntry;
import io.grpc.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RaftServer {
    private static int serverCount = 0;
    private static final Logger logger = Logger
            .getLogger(RaftServer.class.getName());

    private static final int FOLLOWER = 1;
    private static final int CANDIDATE = 2;
    private static final int LEADER = 3;

    private int status = FOLLOWER;
    private Server server;
    private int port;
    private int serverId;
    private Timer timer;
    private ElectionTask electionTask;
    private SyncLogTask syncLogTask;
    private CommitTask commitTask;
    private Random random;
    private int timeout;

    private int currentCommandId;
    private int commandAppendCount;
    /*raft协议用到的成员*/
    private int leaderId;
    //所有Server上的固有状态
    int currentTerm;
    int votedFor = 0;
    Log logs;
    //所有Server上的变化状态
//    int commitIndex;
//    int lastApplied;  已经包含在logs中了

    int voteCount;
    //Leader上的变化状态
    private final int N = 20;
    int[] nextIndex = new int[10];
    int[] matchIndex = new int[10];

    //todo
    private ArrayList<String> addressList;
    private ArrayList<Integer> portList;
    private boolean[] serverAvailable;

    public RaftServer(int port) {
        super();
        serverId = ++serverCount;
        this.port = port + serverId;
        logs = new Log(serverId);

        currentTerm = 1;
        votedFor = 0;

        timer = new Timer();
        random = new Random();

        commitTask = new CommitTask();
        timer.schedule(commitTask, 20 * 1000, 20 * 1000);
    }

    private synchronized void initLeader(int serverNum) {
        status = LEADER;
        leaderId = serverId;

        for (int i = 0; i < serverNum; ++i) {
            nextIndex[i] = logs.getLastIndex() + 1;
            matchIndex[i] = 0;
        }
        electionTask.cancel();
        syncLogTask = new SyncLogTask();
        timer.schedule(syncLogTask, 0, 150);
        //todo 自己下台
//        timer.schedule(new SelfStepDown(), 30000);   //30秒后自己下台
    }

    private synchronized void resetTimeout() {
//        System.out.println("Server" + serverId + " quit candidate!");
        electionTask.cancel();
        timeout = random.nextInt(150) + 300;
        electionTask = new ElectionTask();
        timer.schedule(electionTask, timeout);
    }

    private synchronized void startTimeout() {
        timeout = random.nextInt(150) + 300;
        electionTask = new ElectionTask();
        timer.schedule(electionTask, timeout);
    }

    private void waitForFollower(int CommandId) {
        synchronized (this) {
            currentCommandId = CommandId;
            commandAppendCount = 1;
            try {
                wait();
                System.out.println("get response from follower");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void start() throws IOException {
        ServerBuilder serverBuilder = ServerBuilder.forPort(port);
        serverBuilder.addService(new IOService());
        serverBuilder.addService(new ConsensusService());
        server = serverBuilder.build();

        server.start();
        startTimeout();     //开始进行投票倒计时

        logger.info("Server" + serverId + " started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset
            // by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server" + serverId +
                    " since JVM is shutting down");
            RaftServer.this.stop();
            System.err.println("*** server shut down");
        }));
    }

    public void stop() {
        if (server != null) {
            timer.cancel();
            server.shutdown();
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }


    public void askForVoteTo(int port) {
        VoteRequest.Builder builder = VoteRequest.newBuilder();
        builder.setCandidateId(serverId);
        builder.setTerm(currentTerm);
        int lastLogIndex = logs.getLastIndex();
        builder.setLastLogIndex(lastLogIndex);
        builder.setLatLogTerm(logs.getLastTerm(lastLogIndex));
        VoteRequest request = builder.build();
        VoteReply response;
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", port)
                .usePlaintext(true)
                .build();
        ConsensusGrpc.ConsensusBlockingStub blockingStub = ConsensusGrpc.newBlockingStub(channel);

        try {
            response = blockingStub.requestVote(request);
            if (response.getVoteGranted())
                ++voteCount;
            else {
                if (response.getTerm() > currentTerm) {
                    status = FOLLOWER;
                    currentTerm = response.getTerm();
                    resetTimeout();
                }
            }
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        }

    }

    public void AppendEntriesRPC(boolean getEntries, int sid) {
        LeaderRequest.Builder builder = LeaderRequest.newBuilder();
        synchronized (this) {
            builder.setTerm(currentTerm);
            builder.setLeaderId(serverId);
            builder.setPrevLogIndex(nextIndex[sid - 1] - 1);
            builder.setPrevLogTerm(logs.getLogByIndex(nextIndex[sid - 1] - 1).term);
            builder.setGetEntries(getEntries);
            builder.setLeaderCommit(logs.commitIndex);
        }

        FollowerReply response;
        LeaderRequest request = builder.build();
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 5000 + sid)
                .usePlaintext(true)
                .build();
        ConsensusGrpc.ConsensusBlockingStub blockingStub = ConsensusGrpc.newBlockingStub(channel);
        try {
            response = blockingStub.appendEntries(request);
            //deal with reply
            if (response.getSuccess()) {
                matchIndex[sid - 1] = response.getMatchIndex();
                nextIndex[sid - 1] = response.getMatchIndex() + 1;
                synchronized (this) {
                    if (response.getResponseTocommandId() == currentCommandId)
                        ++commandAppendCount;
                    if (commandAppendCount > serverCount / 2)
                        this.notify();
                }
            } else {
                if (response.getStepDown()) {
                    status = FOLLOWER;
                    syncLogTask.cancel();   //Leader下台
                    currentTerm = response.getTerm();   //更新term
                } else {
                    nextIndex[sid - 1] = response.getSuggestNextIndex();
                }
            }
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        }

    }

    public void getLogs() {
        FollowerRequest.Builder builder = FollowerRequest.newBuilder();
        builder.setSync(true);
        builder.setId(serverId);
        FollowerRequest request = builder.build();

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 5000 + leaderId)
                .usePlaintext(true)
                .build();
        ConsensusGrpc.ConsensusBlockingStub blockingStub = ConsensusGrpc.newBlockingStub(channel);

        Iterator<Entry> entryIterator;
        try {
            entryIterator = blockingStub.syncLog(request);
            while (entryIterator.hasNext()) {
                //append log
                Entry entry = entryIterator.next();
                logs.addLogEntry(entry.getTerm(), entry.getCommand(), entry.getCommandId());
            }
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 5000;
        if (args.length > 0)
            port = Integer.parseInt(args[0]);
        RaftServer server = new RaftServer(port);
        server.start();
        server.blockUntilShutdown();
    }

    class ConsensusService extends ConsensusGrpc.ConsensusImplBase {
        @Override
        public void requestVote(VoteRequest request,
                                StreamObserver<VoteReply> responseObserver) {

            VoteReply.Builder builder = VoteReply.newBuilder();
            if (request.getTerm() > currentTerm) {
                if (request.getLastLogIndex() > logs.getLastIndex()
                        || request.getLastLogIndex() == logs.getLastIndex() && request.getLatLogTerm() == logs.getLastTerm(logs.getLastIndex())) {
                    if (status == LEADER) {
                        status = FOLLOWER;
                        syncLogTask.cancel();   //Leader下台
                    }
                    currentTerm = request.getTerm();    //更新term
                    resetTimeout(); //重置选举计时器

                    builder.setVoteGranted(true);
                } else {
                    builder.setVoteGranted(false);
                }
            } else {
                builder.setVoteGranted(false);
            }

            builder.setTerm(currentTerm);
            VoteReply reply = builder.build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();

        }

        @Override
        public void appendEntries(LeaderRequest request,
                                  StreamObserver<FollowerReply> responseObserver) {
            FollowerReply.Builder builder = FollowerReply.newBuilder();
            if (request.getTerm() > currentTerm) {
                if (status == LEADER) {
                    status = FOLLOWER;
                    syncLogTask.cancel();   //Leader下台
                }
                currentTerm = request.getTerm();
                resetTimeout();
                leaderId = request.getLeaderId();
                if (request.getGetEntries()) {
                    getLogs();  //拷贝日志
                    // TODO 删除不一致的日志，多集群合并的情况
                }
                builder.setSuccess(true);
                //todo 设置回应信息
                builder.setResponseTocommandId(logs.getLogByIndex(logs.getLastIndex()).commandId);
                // set suggest
                if (logs.getLogByIndex(request.getPrevLogIndex()) == null)
                    builder.setSuggestNextIndex(logs.getLastIndex() + 1);
                else {
                    builder.setSuggestNextIndex(request.getPrevLogIndex() + 1);
                }
            } else if (request.getTerm() == currentTerm) {
                if (request.getPrevLogIndex() < logs.getLastIndex()) {
                    builder.setSuccess(false);
                    builder.setStepDown(true);
                } else {
                    if (status == LEADER) {
                        status = FOLLOWER;
                        syncLogTask.cancel();
                    }
                    resetTimeout();
                    leaderId = request.getLeaderId();
                    if (request.getGetEntries()) {
                        getLogs();  //拷贝日志
                    }
                    builder.setSuccess(true);
                    //todo 设置回应信息
                    builder.setResponseTocommandId(logs.getLogByIndex(logs.getLastIndex()).commandId);
                    // set suggest
                    if (logs.getLogByIndex(request.getPrevLogIndex()) == null)
                        builder.setSuggestNextIndex(logs.getLastIndex() + 1);
                    else {
                        builder.setSuggestNextIndex(request.getPrevLogIndex() + 1);
                    }
                }


            } else {
                builder.setSuccess(false);
                builder.setStepDown(true);
            }

            builder.setTerm(currentTerm);
            builder.setMatchIndex(logs.getLastIndex());

            FollowerReply reply = builder.build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void syncLog(FollowerRequest request, StreamObserver<Entry> responseObserver) {
            Entry.Builder builder = Entry.newBuilder();
            int requestId = request.getId();
            if (request.getSync()) {
                for (int i = nextIndex[requestId - 1]; i <= logs.getLastIndex(); ++i) {
                    LogEntry logEntry = logs.getLogByIndex(i);
                    builder.setIndex(i);
                    builder.setTerm(logEntry.term);
                    builder.setCommandId(logEntry.commandId);
                    builder.setCommand(logEntry.command);
                    Entry reply = builder.build();
                    responseObserver.onNext(reply);
                }
                responseObserver.onCompleted();
            }
        }
    }

    //查错用
    private void displayNextIndex() {
        for (int i = 0; i < serverCount; ++i)
            System.out.print(nextIndex[i] + " ");
        System.out.println(logs.getLastIndex());
    }

    class IOService extends RpcIOGrpc.RpcIOImplBase {

        private Collection<ResultUnit> queryResults;

        @Override
        public void command(ClientRequest request,
                            StreamObserver<ServerReply> responseObserver) {
            logger.info("The command of Client:" + request.getCommand());
            ServerReply.Builder builder = ServerReply.newBuilder();
            if (serverId != leaderId) {
                builder.setSuccess(false);
                builder.setRedirect(true);
                builder.setRedirectPort(5000 + leaderId);
            } else {
                //todo fix index
                logs.addLogEntry(currentTerm, request.getCommand(), request.getCommandId());
                ++logs.appliedIndex;
                waitForFollower(request.getCommandId());
                if (DBConnector.update(request.getCommand())) {
                    ++logs.commitIndex;
                    builder.setSuccess(true);
                } else
                    builder.setSuccess(false);
                builder.setRedirect(false);
            }
            ServerReply reply = builder.build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        private void getQueryResult(String sql) {
            queryResults = new LinkedList<ResultUnit>();
            ResultUnit.Builder builder = ResultUnit.newBuilder();
            Iterator<Map<String, Object>> results = DBConnector.get(sql).iterator();

            while (results.hasNext()) {
                Map<String, Object> result = results.next();
                ResultUnit resultUnit = builder.setContent("id:" + result.get("id").toString() + ", v:" + result.get("v").toString()).build();
                queryResults.add(resultUnit);
            }
        }

        @Override
        public void query(ClientRequest request,
                          StreamObserver<ResultUnit> responseObserver) {
            logger.info("The command of Client:" + request.getCommand());
            getQueryResult(request.getCommand());
            for (ResultUnit resultUnit : queryResults)
                responseObserver.onNext(resultUnit);
            responseObserver.onCompleted();
        }
    }

    class ElectionTask extends TimerTask {
        @Override
        public void run() {
            System.out.println("Candidate " + serverId + " ask for vote!");
            status = CANDIDATE;
            //startElection();
            timer.schedule(new ElectionTimeout(), 150);
            ++currentTerm;
            votedFor = serverId;
            voteCount = 1;      //vote for self;
            for (int i = 1; i <= serverCount; ++i) {
                if (i != serverId) {

                    askForVoteTo(5000 + i);
                }
            }
            if (voteCount > serverCount / 2) {
                logger.info("Server " + serverId + " now is Leader!");
                initLeader(serverCount);
//            System.exit(0);
            }
        }
    }

    class SyncLogTask extends TimerTask {
        @Override
        public boolean cancel() {
            voteCount = 0;
            votedFor = 0;
            logger.info("Server" + serverId + " now step out");
            return super.cancel();
        }

        /**
         * send heartbeat to all followers
         */
        @Override
        public void run() {
//            System.out.println("Server" + serverId + " is sending heartbeat");
            for (int i = 1; i <= serverCount; ++i) {
                if (serverId != i) {
                    if (matchIndex[i] == logs.getLastIndex())
                        AppendEntriesRPC(false, i); //已经一致，发送heartbeat包
                    else {
                        AppendEntriesRPC(true, i);  //准备同步日志
                    }
                }
            }
        }
    }

    class ElectionTimeout extends TimerTask {
        @Override
        public void run() {
            if (leaderId != serverId) {
                voteCount = 0;
                votedFor = 0;
                status = FOLLOWER;
                resetTimeout();
            }
        }
    }

    class SelfStepDown extends TimerTask {
        @Override
        public void run() {
            System.out.println("go down");
            status = FOLLOWER;
            syncLogTask.cancel();
        }
    }

    // commitTask
    class CommitTask extends TimerTask {
        @Override
        public void run() {
            logs.storeLog();
        }
    }
}
