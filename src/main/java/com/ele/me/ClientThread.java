package com.ele.me;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientThread extends Thread {
    private static AtomicInteger index = new AtomicInteger(0);

    private CommonClient client;
    private CountDownLatch latch;

    public ClientThread(CommonClient client, CountDownLatch latch) {
        this.client = client;
        this.latch = latch;
    }

    @Override
    public void run() {
        String command;
        for (int j = 0; j < 100; ++j) { //todo 总数暂时不要超过500
            command = getCommand(index.getAndIncrement(), client.id);
            client.commandServer(command);
//            client.dbTest(command);
        }
        latch.countDown();
    }

    private String getCommand(int id, int v) {
        return "INSERT INTO simple VALUES (" + id + "," + v + ")";
    }
}
