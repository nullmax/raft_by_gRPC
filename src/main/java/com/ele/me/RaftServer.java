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
    private final int serverId;

    private Timer[] timers;
    private SyncLogTask[] syncLogTasks;

    private ElectionTask electionTask;
    private CommitTask commitTask;
    private ApplyTask applyTask;
    private Random random;
    private int timeout;

    /*raft协议用到的成员*/
    private int leaderId;
    //所有Server上的固有状态
    int currentTerm;
    int votedFor;
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


    private ManagedChannel[] channels;
    private ConsensusGrpc.ConsensusBlockingStub blockingStubs[];

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
        votedFor = -1;

        random = new Random();
    }

    /**
     * 设置所有Server的地址和端口，并初始化gRPC通道
     *
     * @param addressList
     * @param portList
     */
    public void setCommunicateList(ArrayList<String> addressList, ArrayList<Integer> portList) {
        this.addressList = addressList;
        this.portList = portList;
        serverCount = addressList.size();
        nextIndex = new int[serverCount];
        matchIndex = new int[serverCount];
        channels = new ManagedChannel[serverCount];
        blockingStubs = new ConsensusGrpc.ConsensusBlockingStub[serverCount];

        timers = new Timer[serverCount];
        syncLogTasks = new SyncLogTask[serverCount];
        timers[serverId] = new Timer();
    }

    private void getRPC(int sid) {
        if (channels[sid] != null)
            return;
        else {
            channels[sid] = ManagedChannelBuilder
                    .forAddress(addressList.get(sid), portList.get(sid))
                    .usePlaintext(true)
                    .build();
            blockingStubs[sid] = ConsensusGrpc.newBlockingStub(channels[sid]);
        }
    }

    private synchronized void initLeader(int serverNum) {
        status = LEADER;
        leaderId = serverId;

        for (int i = 0; i < serverNum; ++i) {
            nextIndex[i] = logs.getLastIndex() + 1;
            matchIndex[i] = 0;
        }

        electionTask.cancel();
        for (int i = 0; i < serverCount; ++i) {
            if (i != serverId) {
                syncLogTasks[i] = new SyncLogTask(i);
                timers[i] = new Timer();
                timers[i].schedule(syncLogTasks[i], 0, 150);
            }
        }

//        syncLogTask = new SyncLogTask();
//        timers[serverId].schedule(syncLogTask, 0, 150);
//        todo 自己下台
//        timer.schedule(new SelfStepDown(), 5000);   //5秒后自己下台
        //todo 目前只有leader能够apply
        applyTask = new ApplyTask();
        timers[serverId].schedule(applyTask, 100, 100);
    }

    private synchronized void resetTimeout() {
        electionTask.cancel();
        timeout = random.nextInt(150) + 300;
        electionTask = new ElectionTask();
        timers[serverId].schedule(electionTask, timeout);
    }

    private synchronized void startTimeout() {
        timeout = random.nextInt(150) + 300;
        electionTask = new ElectionTask();
        timers[serverId].schedule(electionTask, timeout);
    }

    public void start() throws IOException {
        ServerBuilder serverBuilder = ServerBuilder.forPort(port);
        serverBuilder.addService(new IOService());
        serverBuilder.addService(new ConsensusService());
        server = serverBuilder.build();

        commitTask = new CommitTask();
        timers[serverId].schedule(commitTask, 20 * 1000, 20 * 1000);

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
            for (int i = 0; i < serverCount; ++i) {
                if (timers[i] != null)
                    timers[i].cancel();
            }
            server.shutdown();
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private void stepDown() {
        status = FOLLOWER;
        voteCount = 0;
        votedFor = -1;
        applyTask.cancel();
        for (int i = 0; i < serverCount; ++i) {
            if (i != serverId && timers[i] != null) {
                timers[i].cancel();
            }
        }
        logger.info("Server" + serverId + " now step out");
    }

    public void askForVoteTo(int sid) {
        VoteRequest.Builder builder = VoteRequest.newBuilder();
        builder.setCandidateId(serverId);
        builder.setTerm(currentTerm);
        int lastLogIndex = logs.getLastIndex();
        builder.setLastLogIndex(lastLogIndex);
        builder.setLatLogTerm(logs.getLastTerm());
        VoteRequest request = builder.build();
        VoteReply response;

        try {
            getRPC(sid);
            response = blockingStubs[sid].requestVote(request);
            if (response.getVoteGranted()) {
                ++voteCount;
            } else {
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
            //todo NPE
            try {
                commandId = logs.getLogByIndex(i).commandId;
            } catch (NullPointerException e) {
                System.out.println(serverId + ", " + i);
                return;
            }
            if (commandLock.get(commandId) != null) {
                if (commandLock.get(commandId).incrementAndGet() == serverCount / 2 + 1) {
                    logs.commitIndex++;
                }
            }
        }
    }

    private Entry logToEntry(LogEntry logEntry) {
        Entry.Builder builder = Entry.newBuilder();
        builder.setTerm(logEntry.term);
        builder.setIndex(logEntry.logIndex);
        builder.setCommandId(logEntry.commandId);
        builder.setCommand(logEntry.command);
        return builder.build();
    }

    public void AppendEntriesRPC(int sid) {
        LeaderRequest.Builder builder = LeaderRequest.newBuilder();

        builder.setTerm(currentTerm);
        builder.setLeaderId(serverId);
        builder.setPrevLogIndex(nextIndex[sid] - 1);
        synchronized (logs) {
            try {
                builder.setPrevLogTerm(logs.getLogByIndex(nextIndex[sid] - 1).term);
            } catch (NullPointerException e) {
                displayNextIndex();
                System.exit(1);
            }
            builder.setLeaderCommit(logs.commitIndex);

            for (int i = nextIndex[sid]; i <= logs.getLastIndex(); ++i) {
                builder.addEntries(logToEntry(logs.getLogByIndex(i)));
            }
        }

        FollowerReply response;
        LeaderRequest request = builder.build();
        try {
            getRPC(sid);
            response = blockingStubs[sid].appendEntries(request);
            //deal with reply
            if (response.getSuccess()) {

                setCommitIndex(nextIndex[sid], response.getMatchIndex());

                matchIndex[sid] = response.getMatchIndex();
                nextIndex[sid] = response.getMatchIndex() + 1;

            } else {
                if (response.getStepDown()) {
                    status = FOLLOWER;
                    stepDown();   //Leader下台
                    resetTimeout();
                    currentTerm = response.getTerm();   //更新term
                } else {
                    nextIndex[sid] = response.getSuggestNextIndex();
                }
            }
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        }

    }

    public static void main(String[] args) throws Exception {
        int port = 5500;
        if (args.length > 0)
            port = Integer.parseInt(args[0]);
        RaftServer server = new RaftServer(port, 0);
        ArrayList<String> addressList = new ArrayList<String>();
        ArrayList<Integer> portList = new ArrayList<>();
        addressList.add("localhost");
        portList.add(5500 + 0);
        server.setCommunicateList(addressList, portList);

        server.start();
        server.blockUntilShutdown();
    }

    class ConsensusService extends ConsensusGrpc.ConsensusImplBase {
        @Override
        public void requestVote(VoteRequest request, StreamObserver<VoteReply> responseObserver) {
            VoteReply.Builder builder = VoteReply.newBuilder();
            if (request.getTerm() < currentTerm) {
                builder.setVoteGranted(false);
            } else if (request.getTerm() > currentTerm) {
                if (status == LEADER) {
                    status = FOLLOWER;
                    stepDown();   //Leader下台
                }
                resetTimeout(); //重置选举计时器
                builder.setVoteGranted(true);
                currentTerm = request.getTerm();
                votedFor = request.getCandidateId();

            } else {
                if (votedFor == -1 || votedFor == request.getCandidateId()) {
                    if (request.getLatLogTerm() > logs.getLastTerm()
                            || request.getLatLogTerm() == logs.getLastTerm() && request.getLastLogIndex() >= logs.getLastIndex()) {
                        resetTimeout(); //重置选举计时器
                        votedFor = request.getCandidateId();
                        builder.setVoteGranted(true);
                    } else {
                        builder.setVoteGranted(false);
                    }
                } else
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
            if (request.getTerm() >= currentTerm) {
                if (status == LEADER && request.getTerm() > currentTerm) {     //Leader下台
                    status = FOLLOWER;
                    stepDown();
                }
                currentTerm = request.getTerm();
                resetTimeout();
                leaderId = request.getLeaderId();
                votedFor = -1;  //reset voteFor

                if (logs.getLogByIndex(request.getPrevLogIndex()) != null &&
                        logs.getLogByIndex(request.getPrevLogIndex()).term == request.getPrevLogTerm()) {
                    // 记录匹配，准备拷贝日志
                    int entriesCount = request.getEntriesCount();
                    if (entriesCount > 0) {
                        try {
                            // 删除同步起始点及之后目录
                            logs.deleteLogEntry(request.getPrevLogIndex() + 1);
                            // 复制日志
                            getRPC(leaderId);
                            Entry entry;
                            for (int i = 0; i < entriesCount; ++i) {
                                entry = request.getEntries(i);
                                logs.addLogEntry(entry.getTerm(), entry.getCommand(), entry.getCommandId());
                            }
                        } catch (StatusRuntimeException e) {
                            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
                            builder.setSuccess(false);
                        }
                        // 拷贝成功，回复Leader
                        builder.setResponseTocommandId(logs.getLogByIndex(logs.getLastIndex()).commandId);
                    }
                    builder.setSuccess(true);
                    builder.setMatchIndex(logs.getLastIndex());
                    if (request.getLeaderCommit() > logs.commitIndex)
                        logs.commitIndex = Math.min(request.getLeaderCommit(), logs.getLastIndex());
                } else {
                    // 记录不匹配
                    builder.setSuccess(false);
                    // 重设同步起始点
                    if (logs.getLogByIndex(request.getPrevLogIndex()) == null) {
                        builder.setSuggestNextIndex(logs.getLastIndex() + 1);
                    } else {
                        builder.setSuggestNextIndex(request.getPrevLogIndex());
                    }
                }
            } else {
                // 让Leader下台
                builder.setSuccess(false);
                builder.setStepDown(true);
            }

            builder.setTerm(currentTerm);
            FollowerReply reply = builder.build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    //todo 删除
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
//            logger.info("The command of Client:" + request.getCommand());
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
//                    builder.setSuccess(DBConnector.update(request.getCommand()));
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
//            logger.info("The command of Client:" + request.getCommand());
            getQueryResult(request.getCommand());
            for (ResultUnit resultUnit : queryResults)
                responseObserver.onNext(resultUnit);
            responseObserver.onCompleted();
        }
    }

    class ElectionTask extends TimerTask {
        @Override
        public void run() {
            status = CANDIDATE;
            //startElection
            timers[serverId].schedule(new ElectionTimeout(), 150);
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
        private int sid;

        // 每个Server对应一个Timer
        SyncLogTask(int id) {
            super();
            sid = id;
        }

        @Override
        public void run() {
            AppendEntriesRPC(sid);
        }
    }

    class ElectionTimeout extends TimerTask {
        @Override
        public void run() {
            if (leaderId != serverId) {
                voteCount = 0;
                votedFor = -1;
                status = FOLLOWER;
                electionTask.cancel();
                resetTimeout();
            }
        }
    }

    class SelfStepDown extends TimerTask {
        @Override
        public void run() {
            status = FOLLOWER;
            stepDown();
            resetTimeout();
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
