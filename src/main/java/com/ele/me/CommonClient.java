package com.ele.me;

import com.ele.io.ClientRequest;
import com.ele.io.ResultUnit;
import com.ele.io.RpcIOGrpc;
import com.ele.io.ServerReply;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CommonClient {
    private static final Logger logger =
            Logger.getLogger(CommonClient.class.getName());

    private ManagedChannel channel;
    private RpcIOGrpc.RpcIOBlockingStub blockingStub;
    private RpcIOGrpc.RpcIOStub asyncStub;

    private static AtomicInteger commandId = new AtomicInteger(0);
    private static AtomicInteger clientCount = new AtomicInteger(0);

    public int id;

    CommonClient(ManagedChannel channel) {
        this.channel = channel;
        blockingStub = RpcIOGrpc.newBlockingStub(channel);
        asyncStub = RpcIOGrpc.newStub(channel);
    }

    public CommonClient(String host, int port) {
        this(ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext(true)
                .build());
        id = clientCount.getAndIncrement();
    }

    /**
     * 设置传输通道
     *
     * @param address
     * @param port
     */
    private void setChannel(String address, int port) {
        channel = ManagedChannelBuilder
                .forAddress(address, port)
                .usePlaintext(true)
                .build();
        blockingStub = RpcIOGrpc.newBlockingStub(channel);
//        logger.info("redirect to:" + address + ":" + port);
    }

    /**
     * 关闭通道
     *
     * @throws InterruptedException
     */
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
    }

    /**
     * 客户端向服务器发送update请求，并接收结果
     *
     * @param command
     */
    public void commandServer(String command) {
//        logger.info("Send \"" + command + "\" to server");
        ClientRequest.Builder builder = ClientRequest.newBuilder();
        builder.setCommand(command);
        builder.setCommandId(commandId.incrementAndGet());

        ClientRequest request = builder.build();
        ServerReply response = null;

        do {
            try {
                response = blockingStub.withDeadlineAfter(10, TimeUnit.SECONDS).command(request);
                if (response.getRedirect()) {
                    setChannel(response.getRedirectAddress(), response.getRedirectPort());
                }
            } catch (StatusRuntimeException e) {

                if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                    logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
                    continue;
                }
                logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
                return;
            }
        } while (response == null || !response.getSuccess());
//        logger.info("Result from server: success");
    }

    /**
     * 客户端以异步方式向服务器发送update请求，并接收结果
     *
     * @param command
     * @param finishLatch 等待接收结果
     */
    public void asyncCommandServer(String command, CountDownLatch finishLatch) {
//        logger.info("Send \"" + command + "\" to server");
        ClientRequest.Builder builder = ClientRequest.newBuilder();
        builder.setCommand(command);
        builder.setCommandId(commandId.incrementAndGet());

        ClientRequest request = builder.build();
        StreamObserver<ServerReply> responseObserver = new StreamObserver<ServerReply>() {
            @Override
            public void onNext(ServerReply value) {
//                logger.info("The result of \'" + command + "\' from server:" + value.getSuccess());
                if (value.getRedirect()) {
                    setChannel(value.getRedirectAddress(), value.getRedirectPort());
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.WARNING, "RPC failed: {0}", t.getMessage());
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                finishLatch.countDown();
            }
        };
        try {
            // 非阻塞传输
            asyncStub.command(request, responseObserver);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        }
    }

    /**
     * 客户端向服务器发送查询请求，并处理接收结果
     *
     * @param query 查询的SQL语句
     */
    public void queryServer(String query) {
        logger.info("Send \"" + query + "\" to server");
        ClientRequest.Builder builder = ClientRequest.newBuilder();
        builder.setCommand(query);
        ClientRequest request = builder.build();

        Iterator<ResultUnit> queryResults;
        try {
            queryResults = blockingStub.query(request);
            logger.info("Result from server:");
            while (queryResults.hasNext()) {
                ResultUnit resultUnit = queryResults.next();
                logger.info(resultUnit.getContent());
            }
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        }
    }

    /**
     * 客户端向服务器发送查询请求，并处理结果
     * 对应服务器不写入数据库的方式
     *
     * @param logIndex
     */
    public void queryRaft(int logIndex) {
        ClientRequest.Builder builder = ClientRequest.newBuilder();
        builder.setCommandId(logIndex);
        ClientRequest request = builder.build();

        Iterator<ResultUnit> queryResults;
        try {
            queryResults = blockingStub.query(request);
//            logger.info("Result from server:");
            while (queryResults.hasNext()) {
                ResultUnit resultUnit = queryResults.next();
//                logger.info(resultUnit.getContent());
            }
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        }
    }

    public static void main(String[] args) throws Exception {
        CommonClient client = new CommonClient("10.101.35.39", 5500);

        try {
            String command = "DELETE FROM simple";
            client.commandServer(command);
            command = "INSERT INTO simple VALUES (1, 9)";
            client.commandServer(command);
            command = "INSERT INTO simple VALUES (2, 8)";
            client.commandServer(command);
            String query = "SELECT * FROM simple";
            client.queryServer(query);
        } finally {
            client.shutdown();
        }
//        try {
//            final CountDownLatch finishLatch = new CountDownLatch(3);
//            String command = "DELETE FROM simple";
//            client.asyncCommandServer(command, finishLatch);
//            for (int i = 0; i < 3; ++i) {
//                command = "INSERT INTO simple VALUES (1, 9)";
//                client.asyncCommandServer(command, finishLatch);
//            }
//            for (int i = 0; i < 5; ++i) {
//                command = "INSERT INTO simple VALUES (2, 8)";
//                client.asyncCommandServer(command, finishLatch);
//            }
//            finishLatch.await();
//        } finally {
//            client.shutdown();
//        }
    }
}
