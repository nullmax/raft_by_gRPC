import com.ele.me.ClientThread;
import com.ele.me.CommonClient;

import java.util.concurrent.CountDownLatch;

public class TestClient {
    private static final int N = 10;
    private static CommonClient[] clients = new CommonClient[N];
    private static long summer1 = 0;
    private static long summer2 = 0;

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < N; ++i)
            clients[i] = new CommonClient("localhost", 5502);
        summer1 += multiTest();
        for (int i = 0; i < 20; ++i)
            summer2 += multiTest();
        summer1 += summer2;
        System.out.println("Mean time1:" + summer1 / 21);
        System.out.println("Mean time2:" + summer2 / 20);
    }

    public static long multiTest() throws Exception {
        ClientThread[] clientThreads = new ClientThread[N];
        CountDownLatch latch = new CountDownLatch(N);
        final CountDownLatch finishLatch = new CountDownLatch(500);
        long beginTime = System.currentTimeMillis();
        for (int i = 0; i < N; ++i) {
            clientThreads[i] = new ClientThread(clients[i], latch, finishLatch);
            clientThreads[i].start();
        }
//        finishLatch.await();
        latch.await();
        long total = System.currentTimeMillis() - beginTime;
        System.out.println("Total time:" + total);
        return total;
    }

    public static long singleTest() throws Exception {
        CommonClient client = new CommonClient("localhost", 5500);
        String command = "INSERT INTO simple VALUES (1, 9)";
        long beginTime = System.currentTimeMillis();
//        client.commandServer(command);
        client.dbTest(command);
        long total = System.currentTimeMillis() - beginTime;
        System.out.println("Total time: " + total);
        return total;
    }
}
