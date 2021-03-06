package com.ele.me;

import com.ele.io.ClientRequest;
import com.ele.io.ResultUnit;
import com.ele.io.RpcIOGrpc;
import com.ele.io.ServerReply;
import com.ele.util.DBConnector;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;

public class CommonServer {
    private static final Logger logger = Logger
            .getLogger(RaftServer.class.getName());
    private Server server;
    private int port;

    private ConcurrentLinkedDeque<Integer> commandIdList;
    private ConcurrentHashMap<Integer, ClientRequest> requestConcurrentHashMap;
    private ConcurrentHashMap<Integer, StreamObserver<ServerReply>> observerConcurrentHashMap;
    private final ResponseThread responseThread;

    CommonServer(int port) {
        this.port = port;
        responseThread = new ResponseThread();
        commandIdList = new ConcurrentLinkedDeque<Integer>();
        requestConcurrentHashMap = new ConcurrentHashMap<Integer, ClientRequest>();
        observerConcurrentHashMap = new ConcurrentHashMap<Integer, StreamObserver<ServerReply>>();
    }

    public static void main(String[] args) throws Exception {
        CommonServer server = new CommonServer(5500);
        server.start();
        server.blockUntilShutdown();
    }

    public void start() throws IOException {

        ServerBuilder serverBuilder = ServerBuilder.forPort(port);
        serverBuilder.addService(new CommonServer.IOService());
        server = serverBuilder.build();

//        responseThread.start();
        server.start();

        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset
            // by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server" +
                    " since JVM is shutting down");
            CommonServer.this.stop();
            System.err.println("*** server shut down");
        }));
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    class IOService extends RpcIOGrpc.RpcIOImplBase {

        private Collection<ResultUnit> queryResults;

//        @Override
//        public void command(ClientRequest request, StreamObserver<ServerReply> responseObserver) {
//            boolean syncFlag = false;
//            if (commandIdList.isEmpty())
//                syncFlag = true;
//
//            logger.info("receive: " + request.getCommand() + ", id:" + request.getCommandId());
//            commandIdList.add(request.getCommandId());
//            requestConcurrentHashMap.put(request.getCommandId(), request);
//            observerConcurrentHashMap.put(request.getCommandId(), responseObserver);
//
//            if (syncFlag) {
//                synchronized (responseThread) {
//                    responseThread.notify();
//                }
//            }
//        }

        @Override
        public void command(ClientRequest request,
                            StreamObserver<ServerReply> responseObserver) {
            ServerReply.Builder builder = ServerReply.newBuilder();
//            builder.setSuccess(DBConnector.update(request.getCommand()));
            builder.setSuccess(true);
            builder.setRedirect(false);

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

    class ResponseThread extends Thread {
        @Override
        public void run() {
            int commandId;
            while (true) {

                if (commandIdList.isEmpty()) {
                    synchronized (this) {
                        try {
                            this.wait();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                commandId = commandIdList.pollFirst();
                ServerReply.Builder builder = ServerReply.newBuilder();
                builder.setSuccess(DBConnector.update(requestConcurrentHashMap.get(commandId).getCommand()));
                builder.setRedirect(false);

                observerConcurrentHashMap.get(commandId).onNext(builder.build());
                observerConcurrentHashMap.get(commandId).onCompleted();

                requestConcurrentHashMap.remove(commandId);
                observerConcurrentHashMap.remove(commandId);
            }
        }
    }
}
