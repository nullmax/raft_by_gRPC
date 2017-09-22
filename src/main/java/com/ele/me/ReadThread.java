package com.ele.me;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class ReadThread extends Thread {
    private CommonClient client;
    private CountDownLatch latch;
    private int count;

    public ReadThread(CommonClient client, CountDownLatch latch, int count) {
        this.client = client;
        this.latch = latch;
        this.count = count;
    }

    @Override
    public void run() {
        Random random = new Random();
        for (int j = 0; j < count; ++j) {
            client.queryRaft(random.nextInt(500) + 1);
        }
        latch.countDown();
    }
}
