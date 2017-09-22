import com.ele.me.CommonClient;
import com.ele.me.ReadThread;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * 测试读取数据库用的客户端
 */
public class ReadClient {

    private static final int N = 100;
    private static CommonClient[] clients = new CommonClient[N];
    private static Random random = new Random();
    private static long summer1 = 0;
    private static long summer2 = 0;

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < N; ++i) {
//            clients[i] = new CommonClient("localhost", 5502);
            clients[i] = new CommonClient("10.101.35.38", 5500);
        }
        summer1 += multiTest();
        for (int i = 0; i < 20; ++i)
            summer2 += multiTest();
        summer1 += summer2;
        System.out.println("Mean time1:" + summer1 / 21);
        System.out.println("Mean time2:" + summer2 / 20);
    }

    public static long multiTest() throws Exception {
        ReadThread[] clientThreads = new ReadThread[N];
        CountDownLatch latch = new CountDownLatch(N);

        long beginTime = System.currentTimeMillis();
        for (int i = 0; i < N; ++i) {
            clientThreads[i] = new ReadThread(clients[i], latch, 1);
            clientThreads[i].start();
        }
        latch.await();
        long total = System.currentTimeMillis() - beginTime;
        System.out.println("Total time:" + total);
        return total;
    }

    public static long singleTest() throws Exception {
        long beginTime = System.currentTimeMillis();
        clients[0].queryRaft(random.nextInt(500) + 1);
        long total = System.currentTimeMillis() - beginTime;
        System.out.println("Total time: " + total);
        return total;
    }
}
