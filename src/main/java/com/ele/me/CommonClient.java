package com.ele.me;

import com.ele.io.ClientRequest;
import com.ele.io.ResultUnit;
import com.ele.io.RpcIOGrpc;
import com.ele.io.ServerReply;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CommonClient {
    private static final Logger logger =
            Logger.getLogger(CommonClient.class.getName());

    private ManagedChannel channel;
    private RpcIOGrpc.RpcIOBlockingStub blockingStub;

    private static AtomicInteger commandId = new AtomicInteger(0);
    private static AtomicInteger clientCount = new AtomicInteger(0);

    public int id;

    CommonClient(ManagedChannel channel) {
        this.channel = channel;
        blockingStub = RpcIOGrpc.newBlockingStub(channel);
    }

    public CommonClient(String host, int port) {
        this(ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext(true)
                .build());
        id = clientCount.getAndIncrement();
    }

    private void setChannel(String address, int port) {
        channel = ManagedChannelBuilder
                .forAddress(address, port)
                .usePlaintext(true)
                .build();
        blockingStub = RpcIOGrpc.newBlockingStub(channel);
        logger.info("redirect to:" + address + ":" + port);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
    }

    public void commandServer(String command) {
        logger.info("Send \"" + command + "\" to server");
        ClientRequest.Builder builder = ClientRequest.newBuilder();
        builder.setCommand(command);
        builder.setCommandId(commandId.incrementAndGet());

        ClientRequest request = builder.build();
        ServerReply response;

        do {
            try {
                response = blockingStub.command(request);
            } catch (StatusRuntimeException e) {
                logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
                return;
            }
            if (response.getRedirect()) {
                setChannel(response.getRedirectAddress(), response.getRedirectPort());
            }
        } while (!response.getSuccess());
        logger.info("Result from server: success");
    }

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

    public static void main(String[] args) throws Exception {
        CommonClient client = new CommonClient("localhost", 5001);
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
    }
}
