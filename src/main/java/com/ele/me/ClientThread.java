package com.ele.me;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientThread extends Thread {
    private static AtomicInteger index = new AtomicInteger(0);

    private CommonClient client;
    private CountDownLatch latch;
    private final CountDownLatch finishLatch;

    public ClientThread(CommonClient client, CountDownLatch latch, CountDownLatch finishLatch) {
        this.client = client;
        this.latch = latch;
        this.finishLatch = finishLatch;
    }

    @Override
    public void run() {
        String command;
        for (int j = 0; j < 5; ++j) {
            command = getCommand(index.getAndIncrement(), client.id);
//            client.asyncCommandServer(command, finishLatch);
            client.commandServer(command);
        }
        latch.countDown();
    }

    private String getCommand(int id, int v) {
        return "INSERT INTO simple VALUES (" + id + "," + v + ")";
    }
}
