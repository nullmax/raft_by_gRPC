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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    private ApplyTask applyTask;
    private Random random;
    private int timeout;

    /*raft协议用到的成员*/
    private int leaderId;
    //所有Server上的固有状态
    int currentTerm;
    int votedFor = -1;
    Log logs;
    //所有Server上的变化状态
//    int commitIndex;
//    int lastApplied;  已经包含在logs中了

    int voteCount;
    //Leader上的变化状态
    int[] nextIndex;
    int[] matchIndex;


    private ConcurrentHashMap<Integer, AtomicInteger> commandLock = new ConcurrentHashMap<Integer, AtomicInteger>();
    private ConcurrentHashMap<Integer, AtomicBoolean> waitLock = new ConcurrentHashMap<Integer, AtomicBoolean>();

    private ArrayList<String> addressList;
    private ArrayList<Integer> portList;

    /**
     * @param port 服务器端口号
     * @param id   0 <= id < 服务器数量
     */
    public RaftServer(int port, int id) {
        super();
        serverId = id;
        this.port = port;

        //this.port = port + serverId;
        logs = new Log(serverId);

        currentTerm = 1;
        votedFor = 0;

        timer = new Timer();
        random = new Random();

        commitTask = new CommitTask();
        timer.schedule(commitTask, 20 * 1000, 20 * 1000);
    }

    public void setCommunicateList(ArrayList<String> addressList, ArrayList<Integer> portList) {
        this.addressList = addressList;
        this.portList = portList;
        serverCount = addressList.size();
        nextIndex = new int[serverCount];
        matchIndex = new int[serverCount];
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
//        timer.schedule(new SelfStepDown(), 20000);   //20秒后自己下台
        //todo 目前只有leader能够apply
        applyTask = new ApplyTask();
        timer.schedule(applyTask, 100, 100);
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


    public void askForVoteTo(int sid) {
        VoteRequest.Builder builder = VoteRequest.newBuilder();
        builder.setCandidateId(serverId);
        builder.setTerm(currentTerm);
        int lastLogIndex = logs.getLastIndex();
        builder.setLastLogIndex(lastLogIndex);
        builder.setLatLogTerm(logs.getLastTerm(lastLogIndex));
        VoteRequest request = builder.build();
        VoteReply response;
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(addressList.get(sid), portList.get(sid))
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

    void setCommitIndex(int start, int end) {
        int commandId;
        for (int i = start; i <= end; ++i) {
            commandId = logs.getLogByIndex(i).commandId;
            if (commandLock.get(commandId) != null) {
                if (commandLock.get(commandId).incrementAndGet() == serverCount / 2 + 1)
                    logs.commitIndex++;
            }
        }
    }

    public void AppendEntriesRPC(boolean getEntries, int sid) {
        LeaderRequest.Builder builder = LeaderRequest.newBuilder();
        synchronized (this) {
            builder.setTerm(currentTerm);
            builder.setLeaderId(serverId);
            builder.setPrevLogIndex(nextIndex[sid] - 1);
            builder.setPrevLogTerm(logs.getLogByIndex(nextIndex[sid] - 1).term);
            builder.setGetEntries(getEntries);
            builder.setLeaderCommit(logs.commitIndex);
        }

        FollowerReply response;
        LeaderRequest request = builder.build();
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(addressList.get(sid), portList.get(sid))
                .usePlaintext(true)
                .build();
        ConsensusGrpc.ConsensusBlockingStub blockingStub = ConsensusGrpc.newBlockingStub(channel);
        try {
            response = blockingStub.appendEntries(request);
            //deal with reply
            if (response.getSuccess()) {

                setCommitIndex(nextIndex[sid], response.getMatchIndex());

                matchIndex[sid] = response.getMatchIndex();
                nextIndex[sid] = response.getMatchIndex() + 1;

            } else {
                if (response.getStepDown()) {
                    System.out.println("I am out by rec app reply!");
                    status = FOLLOWER;
                    syncLogTask.cancel();   //Leader下台
                    currentTerm = response.getTerm();   //更新term
                } else {
                    nextIndex[sid] = response.getSuggestNextIndex();
                }
            }
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        }

    }

    public boolean getLogs(int prevLogIndex, int prevLogTerm, boolean getEntries) {
        if (!getEntries) {
            if (logs.getLogByIndex(prevLogIndex).term != prevLogTerm) {
                //may raise null pointer
                return false;
            } else
                return true;
        }

        FollowerRequest.Builder builder = FollowerRequest.newBuilder();
        builder.setSync(true);
        builder.setId(serverId);
        FollowerRequest request = builder.build();

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(addressList.get(leaderId), portList.get(leaderId))
                .usePlaintext(true)
                .build();
        ConsensusGrpc.ConsensusBlockingStub blockingStub = ConsensusGrpc.newBlockingStub(channel);

        Iterator<Entry> entryIterator;
        try {
            //append log
            entryIterator = blockingStub.syncLog(request);
            while (entryIterator.hasNext()) {
                //todo 删除不一致的记录
                Entry entry = entryIterator.next();
                logs.addLogEntry(entry.getTerm(), entry.getCommand(), entry.getCommandId());
            }
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return false;
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        int port = 5000;
        if (args.length > 0)
            port = Integer.parseInt(args[0]);
        RaftServer server = new RaftServer(port, 1);
        server.start();
        server.blockUntilShutdown();
    }

    class ConsensusService extends ConsensusGrpc.ConsensusImplBase {
        @Override
        public void requestVote(VoteRequest request,
                                StreamObserver<VoteReply> responseObserver) {

            VoteReply.Builder builder = VoteReply.newBuilder();
            if (request.getTerm() >= currentTerm) {
                if (votedFor == -1 || votedFor == request.getCandidateId()) {
                    if (request.getTerm() > logs.getLastTerm(logs.getLastIndex())
                            || request.getLatLogTerm() == logs.getLastTerm(logs.getLastIndex()) && request.getLastLogIndex() >= logs.getLastIndex()) {
                        if (request.getLastLogIndex() > logs.getLastIndex() && status == LEADER) {
                            status = FOLLOWER;
                            System.out.println("I am out by rec vote!");
                            syncLogTask.cancel();   //Leader下台
                        }
                        currentTerm = request.getTerm();    //更新term
                        resetTimeout(); //重置选举计时器
                        votedFor = request.getCandidateId();
                        builder.setVoteGranted(true);
                    } else {
                        builder.setVoteGranted(false);
                    }
                } else
                    builder.setVoteGranted(false);
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
                    System.out.println("I am out by rec append!");
                    syncLogTask.cancel();   //Leader下台
                }
                currentTerm = request.getTerm();
                resetTimeout();
                leaderId = request.getLeaderId();
                votedFor = -1;  //reset voteFor
                //拷贝日志
                if (getLogs(request.getPrevLogIndex(), request.getPrevLogTerm(), request.getGetEntries())) {
                    builder.setSuccess(true);
                    builder.setMatchIndex(logs.getLastIndex());
                } else {
                    builder.setSuccess(false);
                    // set suggest
                    if (logs.getLogByIndex(request.getPrevLogIndex()) == null)
                        builder.setSuggestNextIndex(logs.getLastIndex() + 1);
                    else {
                        builder.setSuggestNextIndex(request.getPrevLogIndex() + 1);
                    }
                }
                if (request.getLeaderCommit() > logs.commitIndex)
                    logs.commitIndex = Math.min(request.getLeaderCommit(), logs.getLastIndex());
                //todo 设置回应信息
                builder.setResponseTocommandId(logs.getLogByIndex(logs.getLastIndex()).commandId);
            } else if (request.getTerm() == currentTerm) {
                if (request.getPrevLogIndex() < logs.getLastIndex()) {
                    builder.setSuccess(false);
                    builder.setStepDown(true);
                } else {
                    resetTimeout();
                    leaderId = request.getLeaderId();
                    //拷贝日志
                    if (getLogs(request.getPrevLogIndex(), request.getPrevLogTerm(), request.getGetEntries())) {
                        builder.setSuccess(true);
                        builder.setMatchIndex(logs.getLastIndex());
                    } else {
                        builder.setSuccess(false);
                        // set suggest
                        if (logs.getLogByIndex(request.getPrevLogIndex()) == null)
                            builder.setSuggestNextIndex(logs.getLastIndex() + 1);
                        else {
                            builder.setSuggestNextIndex(request.getPrevLogIndex() + 1);
                        }
                    }
                    //todo 设置回应信息
                    builder.setResponseTocommandId(logs.getLogByIndex(logs.getLastIndex()).commandId);
                }
            } else {
                builder.setSuccess(false);
                builder.setStepDown(true);
            }

            builder.setTerm(currentTerm);
            FollowerReply reply = builder.build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void syncLog(FollowerRequest request, StreamObserver<Entry> responseObserver) {
            Entry.Builder builder = Entry.newBuilder();
            int requestId = request.getId();
            if (request.getSync()) {
                for (int i = nextIndex[requestId]; i <= logs.getLastIndex(); ++i) {
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

    private boolean dbResult;

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
                builder.setRedirectAddress(addressList.get(leaderId));
                builder.setRedirectPort(portList.get(leaderId));
            } else {
                if (logs.checkAppliedBefore(request.getCommandId()))
                    builder.setSuccess(true);
                else {
                    logs.addLogEntry(currentTerm, request.getCommand(), request.getCommandId());
                    commandLock.put(request.getCommandId(), new AtomicInteger(0));
                    waitLock.put(request.getCommandId(), new AtomicBoolean(false));

                    //todo 待优化
                    synchronized (waitLock.get(request.getCommandId())) {
                        try {
                            waitLock.get(request.getCommandId()).wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    builder.setSuccess(waitLock.get(request.getCommandId()).get());
                }
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
            //startElection
            timer.schedule(new ElectionTimeout(), 150);
            ++currentTerm;
            votedFor = serverId;
            voteCount = 1;      //vote for self;
            for (int i = 0; i < serverCount; ++i) {
                if (i != serverId) {
                    askForVoteTo(i);
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
            status = FOLLOWER;
            voteCount = 0;
            votedFor = -1;
            applyTask.cancel();
            logger.info("Server" + serverId + " now step out");
            return super.cancel();
        }

        /**
         * send heartbeat to all followers
         */
        @Override
        public void run() {
//            System.out.println("Server" + serverId + " is sending heartbeat");
            for (int i = 0; i < serverCount; ++i) {
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
                votedFor = -1;
                status = FOLLOWER;
                resetTimeout();
            }
        }
    }

    class SelfStepDown extends TimerTask {
        @Override
        public void run() {
            status = FOLLOWER;
            syncLogTask.cancel();
        }
    }

    //todo commitTask 现在只要保存日志的功能，follower暂时不能应用到本地数据库
    class CommitTask extends TimerTask {
        @Override
        public void run() {
            logs.storeLog();
        }
    }

    class ApplyTask extends TimerTask {
        @Override
        public void run() {
            while (logs.appliedIndex < logs.commitIndex) {
                LogEntry logEntry = logs.getLogByIndex(++logs.appliedIndex);
                waitLock.get(logEntry.commandId).set(DBConnector.update(logEntry.command));
                //todo 待优化
                synchronized (waitLock.get(logEntry.commandId)) {
                    waitLock.get(logEntry.commandId).notify();
                }
            }
        }
    }
}
