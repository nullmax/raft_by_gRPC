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
    //用来测试两次选举之间的时间
    private static long leadTime = 0;
    private static long crashTime = System.currentTimeMillis();
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
    private LogTask commitTask;
    private ApplyTask applyTask;
    private Random random;
    private int timeout;

    /*raft协议用到的成员*/
    private int leaderId;
    // 所有Server上的固有状态
    private AtomicInteger currentTerm;
    private int votedFor;
    private final Log logs;
    // 所有Server上的变化状态

    // 已经包含在logs中了
//    int commitIndex;
//    int lastApplied;

    private int voteCount;
    //Leader上的变化状态
    private int[] nextIndex;
    private int[] matchIndex;

    private ConcurrentHashMap<Integer, AtomicInteger> commandLock = new ConcurrentHashMap<Integer, AtomicInteger>();
    private ConcurrentHashMap<Integer, StreamObserver<ServerReply>> observerConcurrentHashMap = new ConcurrentHashMap<Integer, StreamObserver<ServerReply>>();


    private ArrayList<String> addressList;
    private ArrayList<Integer> portList;


    // 和其他服务通信的频道和存根
    private ManagedChannel[] channels;
    private ConsensusGrpc.ConsensusBlockingStub[] blockingStubs;

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

    /**
     * 获取传输通道
     *
     * @param sid 服务器id
     */
    private void getRPC(int sid) {
        if (channels[sid] != null)
            return;

        channels[sid] = ManagedChannelBuilder
                .forAddress(addressList.get(sid), portList.get(sid))
                .usePlaintext(true)
                .build();
        blockingStubs[sid] = ConsensusGrpc.newBlockingStub(channels[sid]);
    }

    /**
     * 初始化Leader
     *
     * @param serverNum 服务器数量
     */
    private synchronized void initLeader(int serverNum) {
        leadTime = System.currentTimeMillis();
        System.out.println("选举间隔：" + (leadTime - crashTime));
        status.set(LEADER);
        leaderId = serverId;

        for (int i = 0; i < serverNum; ++i) {
            nextIndex[i] = logs.getLastIndex() + 1;
            matchIndex[i] = 0;
        }

        logs.getAppliedIndex();

        for (int i = 0; i < serverCount; ++i) {
            if (i != serverId) {
                syncLogTasks[i] = new SyncLogTask(i);
                timers[i] = new Timer();
                timers[i].scheduleAtFixedRate(syncLogTasks[i], 0, 10);
            }
        }
        // TODO 自己下台
//        timers[serverId].schedule(new SelfStepDown(), 5000);   //5秒后自己下台
    }

    /**
     * 重置election timeout
     */
    private synchronized void resetTimeout() {
        electionTask.cancel();
        timeout = random.nextInt(500) + 500;
        crashTime = System.currentTimeMillis();
        electionTask = new ElectionTask();
        timers[serverId].schedule(electionTask, timeout);
    }

    /**
     * 初始化选举计时器
     */
    private synchronized void startTimeout() {
        timeoutCount = new AtomicInteger(5);    // 初始值要不小于timeout的次数
        timeout = random.nextInt(500) + 500;
        crashTime = System.currentTimeMillis();
        electionTask = new ElectionTask();
        timers[serverId].schedule(electionTask, timeout);
    }

    /**
     * 初始化服务器
     *
     * @throws IOException
     */
    public void start() throws IOException {
        ServerBuilder serverBuilder = ServerBuilder.forPort(port);
        serverBuilder.addService(new IOService());
        serverBuilder.addService(new ConsensusService());
        server = serverBuilder.build();

        commitTask = new LogTask();
        applyTask = new ApplyTask();

        timers[serverId].schedule(commitTask, 1000, 1000);
        timers[serverId].scheduleAtFixedRate(applyTask, 5, 5);

        server.start();
        startTimeout();     //开始进行投票倒计时

        logger.info("Server" + serverId + " started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset
            // by its JVM shutdown hook.
            displayNextIndex();
            System.err.println("commitIndex:" + logs.commitIndex + ", appliedIndex:" + logs.appliedIndex);
            System.err.println("*** shutting down gRPC server" + serverId +
                    " since JVM is shutting down");
            RaftServer.this.stop();
            System.err.println("*** server shut down");
        }));
    }

    /**
     * 停止服务器
     */
    public void stop() {
        if (server != null) {
            for (int i = 0; i < serverCount; ++i) {
                if (timers[i] != null)
                    timers[i].cancel();
            }
            server.shutdown();
        }
    }

    /**
     * 服务器待机
     *
     * @throws InterruptedException
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Leader下台
     *
     * @param reason Leader下台的原因，查错用
     */

    private void stepDown(String reason) {
        crashTime = System.currentTimeMillis();
        status.set(FOLLOWER);
        voteCount = 0;
        votedFor = -1;
        for (int i = 0; i < serverCount; ++i) {
            if (i != serverId && timers[i] != null) {
                timers[i].cancel();
            }
        }

        commandLock.clear();
        observerConcurrentHashMap.clear();
        logger.info("Server" + serverId + " now step out: " + reason);
    }

    /**
     * 向Server拉选票
     *
     * @param sid 目标Server的id
     */
    private void askForVoteTo(int sid) {
        VoteRequest.Builder builder = VoteRequest.newBuilder();
        builder.setCandidateId(serverId);
        builder.setTerm(currentTerm.get());
        builder.setLastLogIndex(logs.getLastIndex());
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


    /**
     * 论文中的间接提交方式，同步结束之后matchIndex会更新，此时确认新的commitIndex
     */
    private void setCommitIndex() {
        int[] temp;
        synchronized (this) {
            temp = Arrays.copyOf(matchIndex, matchIndex.length);
        }

        Arrays.sort(temp);
        int majorMatchIndex = temp[matchIndex.length / 2];
        if (majorMatchIndex > logs.commitIndex && logs.getLogByIndex(majorMatchIndex).term == currentTerm.get())
            logs.commitIndex = majorMatchIndex;
    }

    /**
     * 将日志项转换为传输所用的数据类型
     * Entry类是gRPC自动生成的，logEntry类是自己定义的
     *
     * @param logEntry
     * @return
     */
    private Entry logToEntry(LogEntry logEntry) {
        Entry.Builder builder = Entry.newBuilder();
        if (logEntry != null) {
            builder.setTerm(logEntry.term);
            builder.setIndex(logEntry.logIndex);
            builder.setCommandId(logEntry.commandId);
            builder.setCommand(logEntry.command);
        } else {
            displayNextIndex();
            System.exit(1);
        }
        return builder.build();
    }

    /**
     * 向Follower发送heartbeat包
     *
     * @param sid
     */
    private void AppendEntriesRPC(int sid) {
        LeaderRequest.Builder builder = LeaderRequest.newBuilder();

        builder.setTerm(currentTerm.get());
        builder.setLeaderId(serverId);
        builder.setPrevLogIndex(nextIndex[sid] - 1);

        try {
            builder.setPrevLogTerm(logs.getLogByIndex(nextIndex[sid] - 1).term);
        } catch (NullPointerException e) {
            System.out.println(nextIndex[sid] - 1);
            displayNextIndex();
            System.exit(1);
        }

        builder.setLeaderCommit(logs.commitIndex);
        for (int i = nextIndex[sid], j = 0; j < 10 && i <= logs.getLastIndex(); ++i, ++j) {
            LogEntry logEntry = logs.getLogByIndex(i);
            if (logEntry == null) {
                System.out.println(i);
                displayNextIndex();
                System.exit(1);
            }
            builder.addEntries(logToEntry(logEntry));
        }

        FollowerReply response;
        LeaderRequest request = builder.build();
        try {
            getRPC(sid);
            response = blockingStubs[sid].appendEntries(request);

            //deal with reply
            if (status.compareAndSet(LEADER, LEADER)) {
                if (response.getSuccess()) {
                    matchIndex[sid] = response.getMatchIndex();
                    nextIndex[sid] = response.getMatchIndex() + 1;
                    setCommitIndex();   // 间接提交方式
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
        int id = 2;
        if (args.length > 0)
            id = Integer.parseInt(args[0]);
        RaftServer server = new RaftServer(port, id);
        ArrayList<String> addressList = new ArrayList<>();
        ArrayList<Integer> portList = new ArrayList<>();
//        addressList.add("localhost");
        addressList.add("10.101.35.37");    //remote1
        addressList.add("10.101.35.38");    //remote2
        addressList.add("10.101.35.39");    //remote3

        portList.add(5500);
        portList.add(5500);
        portList.add(5500);
        server.setCommunicateList(addressList, portList);

        server.start();
        server.blockUntilShutdown();
    }

    /**
     * 维持系统一致性的服务
     */
    class ConsensusService extends ConsensusGrpc.ConsensusImplBase {
        /**
         * 处理requestVote RPC
         *
         * @param request          Candidate发来的拉选票请求
         * @param responseObserver
         */
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

        /**
         * 处理AppendEntries RPC
         *
         * @param request          Leader发来的heartbeat包
         * @param responseObserver
         */
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
                        // 删除同步起始点及之后目录
                        logs.deleteLogEntry(request.getPrevLogIndex() + 1);
                        // 记录匹配，准备拷贝日志
                        int entriesCount = request.getEntriesCount();
                        if (entriesCount > 0) {
                            // 复制日志
                            Entry entry;
                            for (int i = 0; i < entriesCount; ++i) {
                                entry = request.getEntries(i);
                                logs.addLogEntry(entry.getTerm(), entry.getCommand(), entry.getCommandId());
                            }
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
                            logs.deleteLogEntry(request.getPrevLogIndex());
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

                    LogEntry logEntry = logs.getLogByIndex(request.getPrevLogIndex());
                    if (logEntry != null && logEntry.term == request.getPrevLogTerm()) {
                        try {
                            // 删除同步起始点及之后目录
                            logs.deleteLogEntry(request.getPrevLogIndex() + 1);
                            // 记录匹配，准备拷贝日志
                            int entriesCount = request.getEntriesCount();
                            if (entriesCount > 0) {
                                // 复制日志
                                Entry entry;
                                for (int i = 0; i < entriesCount; ++i) {
                                    entry = request.getEntries(i);
                                    logs.addLogEntry(entry.getTerm(), entry.getCommand(), entry.getCommandId());
                                }
                            }

                            builder.setSuccess(true);
                            builder.setMatchIndex(logs.getLastIndex());
                            if (request.getLeaderCommit() > logs.commitIndex)
                                logs.commitIndex = Math.min(request.getLeaderCommit(), logs.getLastIndex());
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.exit(1);
                        }
                    } else {
                        // 记录不匹配
                        builder.setSuccess(false);
                        // 重设同步起始点
                        if (logs.getLogByIndex(request.getPrevLogIndex()) == null) {
                            logs.deleteLogEntry(request.getPrevLogIndex()); // 删除不匹配的记录
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

    private void displayNextIndex() {
        for (int i = 0; i < serverCount; ++i)
            System.out.print(nextIndex[i] + " ");
        System.out.println(logs.getLastIndex());
    }

    /**
     * 负责和客户端通信的服务
     */

    class IOService extends RpcIOGrpc.RpcIOImplBase {

        /**
         * 用来执行客户端发送的update操作
         *
         * @param request          客户端发送的请求
         * @param responseObserver
         */
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
                    ServerReply.Builder builder = ServerReply.newBuilder();
                    builder.setSuccess(true);
                    builder.setRedirect(false);
                    responseObserver.onNext(builder.build());
                    responseObserver.onCompleted();

                } else {
                    if (logs.checkAppendBefore(request.getCommandId()))
                        observerConcurrentHashMap.replace(request.getCommandId(), responseObserver); // 更新responseObserver
                    else {
                        logs.addLogEntry(currentTerm.get(), request.getCommand(), request.getCommandId());
                        commandLock.put(request.getCommandId(), new AtomicInteger(0));
                        observerConcurrentHashMap.put(request.getCommandId(), responseObserver);
                    }
                }
            }
        }

        private Collection<ResultUnit> queryResults;

        /**
         * 查询数据库，并将结果转换成要发送的数据格式，存入一个Collection集合里
         *
         * @param sql 客户端发送的查询指令
         */
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

        /**
         * 用来执行客户端发送的查询操作
         *
         * @param request          客户端发送的请求
         * @param responseObserver
         */
        @Override
        public void query(ClientRequest request,
                          StreamObserver<ResultUnit> responseObserver) {
            // 从数据库查询结果
//            logger.info("The command of Client:" + request.getCommand());
//            getQueryResult(request.getCommand());
//            for (ResultUnit resultUnit : queryResults)
//                responseObserver.onNext(resultUnit);
//            responseObserver.onCompleted();

            // 从内存查询结果
            ResultUnit.Builder builder = ResultUnit.newBuilder();
            builder.setContent(logs.getLogByIndex(request.getCommandId()).command);
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }
    }

    /**
     * 执行选举操作的定时任务
     */
    class ElectionTask extends TimerTask {
        @Override
        public void run() {
            // TODO 选举计数
            if (timeoutCount.incrementAndGet() < 1) {
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
            }
        }
    }

    /**
     * 发送heartbeat包的定时任务
     * 每个任务在对应一个Timer可以提高并行性
     */
    class SyncLogTask extends TimerTask {
        private int sid;

        SyncLogTask(int id) {
            super();
            sid = id;
        }

        /**
         * 输出的是执行RPC请求的时间，根据这个可以来设置election timeout
         */
        @Override
        public void run() {
            long beginTime = System.currentTimeMillis();
            AppendEntriesRPC(sid);
            long total = System.currentTimeMillis() - beginTime;
            if (total > 10)
                System.out.println(sid + " Append time:" + total);
        }
    }

    /**
     * 如果发生选举超时，则进行重新选举
     */
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

    /**
     * 自己下台的定时任务，用来测试故障恢复的时间
     */
    class SelfStepDown extends TimerTask {
        @Override
        public void run() {
            status.set(FOLLOWER);
            stepDown("self crash");
            resetTimeout();
        }
    }

    /**
     * 存储和释放日志的定时任务
     */
    class LogTask extends TimerTask {
        @Override
        public void run() {
            try {
                logs.storeLog();
                // TODO 释放已经应用到状态机的日志
//                logs.clearLog();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    /**
     * 将客户端请求应用的本地数据库的定时任务
     */
    class ApplyTask extends TimerTask {
        @Override
        public void run() {
            while (logs.appliedIndex < logs.commitIndex) {
                try {
                    LogEntry logEntry = logs.getLogByIndex(++logs.appliedIndex);
                    // TODO 写数据库部分
//                    boolean result = DBConnector.update(logEntry.command);
                    boolean result = true;
                    if (status.get() == LEADER) {
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
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
    }
}
