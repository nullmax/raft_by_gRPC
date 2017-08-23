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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RaftServer {
    private static int serverCount = 0;
    private static final Logger logger = Logger.getLogger(RaftServer.class.getName());

    private static final int FOLLOWER = 1;
    private static final int CANDIDATE = 2;
    private static final int LEADER = 3;

    private volatile AtomicInteger status;
    private Server server;
    private int port;
    private final int serverId;


    // 定时任务
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
    private AtomicInteger currentTerm;
    private int votedFor;
    private final Log logs;
    //所有Server上的变化状态

//    int commitIndex;
//    int lastApplied;  已经包含在logs中了

    private int voteCount;
    //Leader上的变化状态
    private int[] nextIndex;
    private int[] matchIndex;


    // 在本地实现是由多个Server共享，方便Leader变更之后继续响应客户端请求
    // 如果是在服务器集群中，客户端会重发请求到新的Leader
    private static ConcurrentHashMap<Integer, AtomicInteger> commandLock = new ConcurrentHashMap<Integer, AtomicInteger>();
    private static ConcurrentHashMap<Integer, StreamObserver<ServerReply>> observerConcurrentHashMap = new ConcurrentHashMap<Integer, StreamObserver<ServerReply>>();


    private ArrayList<String> addressList;
    private ArrayList<Integer> portList;


    // 和其他服务通信的频道和存根
    private ManagedChannel[] channels;
    private ConsensusGrpc.ConsensusBlockingStub blockingStubs[];

    //选举计数，当多次未收到heartbeat包才开始选举
    private AtomicInteger timeoutCount;

    /**
     * @param port 服务器端口号
     * @param id   0 <= id < 服务器数量
     */
    public RaftServer(int port, int id) {
        super();
        status = new AtomicInteger(FOLLOWER);
        serverId = id;
        this.port = port;

        //this.port = port + serverId;
        logs = new Log(serverId);

        currentTerm = new AtomicInteger(1);
        votedFor = -1;
        random = new Random();
    }

    /**
     * 设置所有Server的地址和端口，并初始化gRPC通道
     *
     * @param addressList 服务器地址列表
     * @param portList    服务器端口列表
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

        channels[sid] = ManagedChannelBuilder
                .forAddress(addressList.get(sid), portList.get(sid))
                .usePlaintext(true)
                .build();
        blockingStubs[sid] = ConsensusGrpc.newBlockingStub(channels[sid]);
    }

    private synchronized void initLeader(int serverNum) {
        status.set(LEADER);
        leaderId = serverId;

        for (int i = 0; i < serverNum; ++i) {
            nextIndex[i] = logs.getLastIndex() + 1;
            matchIndex[i] = 0;
        }

        logs.getAppliedIndex();
//        System.out.println(logs.appliedIndex + ", " + logs.commitIndex);
        for (int i = 0; i < serverCount; ++i) {
            if (i != serverId) {
                syncLogTasks[i] = new SyncLogTask(i);
                timers[i] = new Timer();
                // todo 间隔
                timers[i].scheduleAtFixedRate(syncLogTasks[i], 0, 10);
//                timers[i].scheduleAtFixedRate(syncLogTasks[i], 0, 20);
            }
        }
//         todo 自己下台
//        timer.schedule(new SelfStepDown(), 5000);   //5秒后自己下台

    }

    private synchronized void resetTimeout() {
        electionTask.cancel();
        timeout = random.nextInt(500) + 1000;
        electionTask = new ElectionTask();
        timers[serverId].schedule(electionTask, timeout);
    }

    private synchronized void startTimeout() {
        timeoutCount = new AtomicInteger(5);    // 初始值要不小于timeout的次数
        timeout = random.nextInt(500) + 1000;
        electionTask = new ElectionTask();
        timers[serverId].schedule(electionTask, timeout);
    }

    public void start() throws IOException {
        ServerBuilder serverBuilder = ServerBuilder.forPort(port);
        serverBuilder.addService(new IOService());
        serverBuilder.addService(new ConsensusService());
        server = serverBuilder.build();

        commitTask = new CommitTask();
        applyTask = new ApplyTask();

        timers[serverId].schedule(commitTask, 20 * 1000, 20 * 1000);
        timers[serverId].scheduleAtFixedRate(applyTask, 5, 5);

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

    /**
     * @param reason Leader下台的原因，查错用
     */

    private void stepDown(String reason) {
        status.set(FOLLOWER);
        voteCount = 0;
        votedFor = -1;
        applyTask.cancel();
        for (int i = 0; i < serverCount; ++i) {
            if (i != serverId && timers[i] != null) {
                timers[i].cancel();
            }
        }
        logger.info("Server" + serverId + " now step out: " + reason);
    }

    private void askForVoteTo(int sid) {
        VoteRequest.Builder builder = VoteRequest.newBuilder();
        builder.setCandidateId(serverId);
        builder.setTerm(currentTerm.get());
        int lastLogIndex = logs.getLastIndex();
        builder.setLastLogIndex(lastLogIndex);
        builder.setLatLogTerm(logs.getLastTerm());
        VoteRequest request = builder.build();
        VoteReply response;

        try {
            getRPC(sid);
            response = blockingStubs[sid].requestVote(request);
            if (status.compareAndSet(CANDIDATE, CANDIDATE)) {
                if (response.getVoteGranted()) {
                    ++voteCount;
                } else {
                    if (response.getTerm() > currentTerm.get()) {
                        status.set(FOLLOWER);
                        currentTerm.set(response.getTerm());
                        timeoutCount.set(0);    // 重置计数
                        resetTimeout();
                    }
                }
            }
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        }

    }

    private void setCommitIndex(int start, int end) {
        int commandId;
        for (int i = start; i <= end; ++i) {
            //todo NPE
            try {
                commandId = logs.getLogByIndex(i).commandId;
            } catch (NullPointerException e) {
                System.out.println(serverId + ", " + i);
                displayNextIndex();
                System.exit(1);
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

    private void AppendEntriesRPC(int sid) {
        LeaderRequest.Builder builder = LeaderRequest.newBuilder();

        builder.setTerm(currentTerm.get());
        builder.setLeaderId(serverId);
        builder.setPrevLogIndex(nextIndex[sid] - 1);
        synchronized (logs) {
            try {
                builder.setPrevLogTerm(logs.getLogByIndex(nextIndex[sid] - 1).term);
            } catch (NullPointerException e) {
                System.out.println(nextIndex[sid] - 1);
                displayNextIndex();
                System.exit(1);
            }
            builder.setLeaderCommit(logs.commitIndex);
            // todo 阈值
            for (int i = nextIndex[sid], j = 0; j < 5 && i <= logs.getLastIndex(); ++i, ++j) {
                builder.addEntries(logToEntry(logs.getLogByIndex(i)));
            }
        }

        FollowerReply response;
        LeaderRequest request = builder.build();
        try {
            getRPC(sid);
            response = blockingStubs[sid].appendEntries(request);
            //deal with reply
            if (status.compareAndSet(LEADER, LEADER)) {
                if (response.getSuccess()) {
                    setCommitIndex(nextIndex[sid], response.getMatchIndex());
                    matchIndex[sid] = response.getMatchIndex();
                    nextIndex[sid] = response.getMatchIndex() + 1;
                } else {
                    if (response.getStepDown()) {
                        status.set(FOLLOWER);
                        if (currentTerm.get() < response.getTerm())
                            stepDown("higher term");   //Leader下台
                        else
                            stepDown("newer logs");   //Leader下台
                        timeoutCount.set(0);    // 重置计数
                        resetTimeout();
                        currentTerm.set(response.getTerm());   //更新term
                    } else {
                        nextIndex[sid] = response.getSuggestNextIndex();
                    }
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
        ArrayList<String> addressList = new ArrayList<>();
        ArrayList<Integer> portList = new ArrayList<>();
        addressList.add("localhost");
        portList.add(5500);
        server.setCommunicateList(addressList, portList);

        server.start();
        server.blockUntilShutdown();
    }

    class ConsensusService extends ConsensusGrpc.ConsensusImplBase {
        @Override
        public void requestVote(VoteRequest request, StreamObserver<VoteReply> responseObserver) {
            VoteReply.Builder builder = VoteReply.newBuilder();
            // Leader处理
            if (status.compareAndSet(LEADER, LEADER)) {
                if (request.getLatLogTerm() > logs.getLastTerm()
                        || request.getLatLogTerm() == logs.getLastTerm() && request.getLastLogIndex() > logs.getLastIndex()) {
                    votedFor = request.getCandidateId();
                    currentTerm.set(request.getTerm());
                    builder.setVoteGranted(true);
                    timeoutCount.set(0);    // 重置计数
                    resetTimeout();
                    stepDown("other candidate");
                } else
                    builder.setVoteGranted(false);
            }
            // Candidate处理
            else if (status.compareAndSet(CANDIDATE, CANDIDATE)) {
                if (request.getLatLogTerm() > logs.getLastTerm()
                        || request.getLatLogTerm() == logs.getLastTerm() && request.getLastLogIndex() > logs.getLastIndex()) {
                    votedFor = request.getCandidateId();
                    currentTerm.set(request.getTerm());
                    status.set(FOLLOWER);
                    timeoutCount.set(0);    // 重置计数
                    resetTimeout();
                    builder.setVoteGranted(true);
                } else
                    builder.setVoteGranted(false);
            }
            // Follower处理
            else {
                timeoutCount.set(0);    // 重置计数
                resetTimeout(); //重置选举计时器
                if (request.getTerm() < currentTerm.get()) {
                    builder.setVoteGranted(false);
                } else {
                    if (votedFor == -1 || votedFor == request.getCandidateId()) {
                        if (request.getLatLogTerm() > logs.getLastTerm()
                                || request.getLatLogTerm() == logs.getLastTerm() && request.getLastLogIndex() >= logs.getLastIndex()) {
                            votedFor = request.getCandidateId();
                            currentTerm.set(request.getTerm());
                            builder.setVoteGranted(true);
                        } else {
                            builder.setVoteGranted(false);
                        }
                    } else
                        builder.setVoteGranted(false);
                }
            }

            builder.setTerm(currentTerm.get());
            VoteReply reply = builder.build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void appendEntries(LeaderRequest request,
                                  StreamObserver<FollowerReply> responseObserver) {
            FollowerReply.Builder builder = FollowerReply.newBuilder();

            // Leader处理
            if (status.compareAndSet(LEADER, LEADER)) {
                if (request.getTerm() > currentTerm.get() ||
                        request.getTerm() == currentTerm.get() && request.getLeaderCommit() >= logs.commitIndex) {     //Leader下台
                    status.set(FOLLOWER);
                    stepDown("other leader");
                    timeoutCount.set(0);    // 重置计数
                    resetTimeout();

                    currentTerm.set(request.getTerm());
                    leaderId = request.getLeaderId();
                    votedFor = -1;  //reset voteFor

                    if (logs.getLogByIndex(request.getPrevLogIndex()) != null &&
                            logs.getLogByIndex(request.getPrevLogIndex()).term == request.getPrevLogTerm()) {
                        // 记录匹配，准备拷贝日志
                        int entriesCount = request.getEntriesCount();
                        if (entriesCount > 0) {
                            // 删除同步起始点及之后目录
                            logs.deleteLogEntry(request.getPrevLogIndex() + 1);
                            // 复制日志
                            Entry entry;
                            for (int i = 0; i < entriesCount; ++i) {
                                entry = request.getEntries(i);
                                logs.addLogEntry(entry.getTerm(), entry.getCommand(), entry.getCommandId());
                            }
                            // 拷贝成功，回复Leader
                            builder.setResponseTocommandId(logs.getLogByIndex(logs.getLastIndex()).commandId);
                            builder.setSuccess(true);
                            builder.setMatchIndex(logs.getLastIndex());
                            if (request.getLeaderCommit() > logs.commitIndex)
                                logs.commitIndex = Math.min(request.getLeaderCommit(), logs.getLastIndex());
                        } else {
                            if (logs.getLastIndex() > request.getPrevLogIndex()) {
                                builder.setSuccess(false);  //记录不如Follower新，下台
                                builder.setStepDown(true);
                            } else {
                                builder.setSuccess(true);
                                builder.setMatchIndex(logs.getLastIndex());
                                if (request.getLeaderCommit() > logs.commitIndex)
                                    logs.commitIndex = Math.min(request.getLeaderCommit(), logs.getLastIndex());
                            }
                        }
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
                    builder.setSuccess(false);
                    builder.setStepDown(true);
                }
            }
            // 其他处理
            else {
                if (request.getTerm() >= currentTerm.get()) {
                    timeoutCount.set(0);    // 重置计数
                    resetTimeout();
                    currentTerm.set(request.getTerm());
                    leaderId = request.getLeaderId();
                    votedFor = -1;  //reset voteFor

                    if (logs.getLogByIndex(request.getPrevLogIndex()) != null &&
                            logs.getLogByIndex(request.getPrevLogIndex()).term == request.getPrevLogTerm()) {
                        // 记录匹配，准备拷贝日志
                        int entriesCount = request.getEntriesCount();
                        if (entriesCount > 0) {
                            // 删除同步起始点及之后目录
                            logs.deleteLogEntry(request.getPrevLogIndex() + 1);
                            // 复制日志
                            Entry entry;
                            for (int i = 0; i < entriesCount; ++i) {
                                entry = request.getEntries(i);
                                logs.addLogEntry(entry.getTerm(), entry.getCommand(), entry.getCommandId());
                            }

                            // 拷贝成功，回复Leader
                            builder.setResponseTocommandId(logs.getLogByIndex(logs.getLastIndex()).commandId);
                            builder.setSuccess(true);
                            builder.setMatchIndex(logs.getLastIndex());
                            if (request.getLeaderCommit() > logs.commitIndex)
                                logs.commitIndex = Math.min(request.getLeaderCommit(), logs.getLastIndex());
                        } else {
                            if (logs.getLastIndex() > request.getPrevLogIndex()) {
                                builder.setSuccess(false);  //记录不如Follower新，下台
                                builder.setStepDown(true);
                            } else {
                                builder.setSuccess(true);
                                builder.setMatchIndex(logs.getLastIndex());
                                if (request.getLeaderCommit() > logs.commitIndex)
                                    logs.commitIndex = Math.min(request.getLeaderCommit(), logs.getLastIndex());
                            }
                        }
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
                    builder.setSuccess(false);
                    builder.setStepDown(true);
                }
            }


            builder.setTerm(currentTerm.get());
            responseObserver.onNext(builder.build());
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

        @Override
        public void command(ClientRequest request,
                            StreamObserver<ServerReply> responseObserver) {
//            logger.info("The command of Client:" + request.getCommand());

            if (serverId != leaderId) {
                ServerReply.Builder builder = ServerReply.newBuilder();
                builder.setSuccess(false);
                builder.setRedirect(true);
                builder.setRedirectAddress(addressList.get(leaderId));
                builder.setRedirectPort(portList.get(leaderId));
                responseObserver.onNext(builder.build());
                responseObserver.onCompleted();
            } else {
                if (logs.checkAppliedBefore(request.getCommandId())) {

                    if (observerConcurrentHashMap.get(request.getCommandId()) != null) {
                        observerConcurrentHashMap.remove(request.getCommandId());   // todo 删除过期链接
                    }
                    ServerReply.Builder builder = ServerReply.newBuilder();
                    builder.setSuccess(true);
                    builder.setRedirect(false);
                    responseObserver.onNext(builder.build());
                    responseObserver.onCompleted();
                } else {
                    logs.addLogEntry(currentTerm.get(), request.getCommand(), request.getCommandId());
                    commandLock.put(request.getCommandId(), new AtomicInteger(0));
                    observerConcurrentHashMap.put(request.getCommandId(), responseObserver);
                }
            }
        }

        private Collection<ResultUnit> queryResults;

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

            // TODO 选举计数
            if (timeoutCount.incrementAndGet() < 5) {
                resetTimeout();
                return;
            }

            status.set(CANDIDATE);
            //startElection
            timers[serverId].schedule(new ElectionTimeout(), 150);
            currentTerm.incrementAndGet();
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
//            long beginTime = System.currentTimeMillis();
            AppendEntriesRPC(sid);
//            long total = System.currentTimeMillis() - beginTime;
//            if (total > 10)
//            System.out.println(sid + " Total time:" + total);
        }
    }

    class ElectionTimeout extends TimerTask {
        @Override
        public void run() {
            if (leaderId != serverId) {
                voteCount = 0;
                votedFor = -1;
                status.set(FOLLOWER);
                resetTimeout();
            }
        }
    }

    class SelfStepDown extends TimerTask {
        @Override
        public void run() {
            status.set(FOLLOWER);
            stepDown("self crash");
            resetTimeout();
        }
    }

    class CommitTask extends TimerTask {
        @Override
        public void run() {
            logs.storeLog();
            logs.clearLog();
        }
    }

    class ApplyTask extends TimerTask {
        @Override
        public void run() {
            while (logs.appliedIndex < logs.commitIndex) {
                LogEntry logEntry = logs.getLogByIndex(++logs.appliedIndex);
                if (status.get() == LEADER) {
                    boolean result = DBConnector.update(logEntry.command);
                    if (observerConcurrentHashMap.get(logEntry.commandId) != null) {
                        ServerReply.Builder builder = ServerReply.newBuilder();
                        builder.setSuccess(result);
                        builder.setRedirect(false);
                        observerConcurrentHashMap.get(logEntry.commandId).onNext(builder.build());
                        observerConcurrentHashMap.get(logEntry.commandId).onCompleted();

                        observerConcurrentHashMap.remove(logEntry.commandId);
                        commandLock.remove(logEntry.commandId);
                    }
                }
            }
        }
    }
}
