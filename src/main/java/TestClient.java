import com.ele.me.ClientThread;
import com.ele.me.CommonClient;

import java.util.concurrent.CountDownLatch;

/**
 * 测试写入数据库用的客户端
 */
public class TestClient {
    private static final int N = 100;
    private static CommonClient[] clients = new CommonClient[N];
    private static long summer1 = 0;
    private static long summer2 = 0;
    private static String singleCommand = "INSERT INTO simple VALUES (1, 9)";

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < N; ++i) {
//            clients[i] = new CommonClient("localhost", 5502);
            clients[i] = new CommonClient("10.101.35.37", 5500);
        }
        summer1 += multiTest();
        for (int i = 0; i < 20; ++i)
            summer2 += multiTest();
        summer1 += summer2;
        System.out.println("Mean time1:" + summer1 / 21);
        System.out.println("Mean time2:" + summer2 / 20);
    }

    /**
     * 向客户端发送500条update指令进行测试
     *
     * @return 返回值是发送到收到回复的时间
     * @throws Exception
     */
    public static long multiTest() throws Exception {
        ClientThread[] clientThreads = new ClientThread[N];
        CountDownLatch latch = new CountDownLatch(N);
        final CountDownLatch finishLatch = new CountDownLatch(500);
        long beginTime = System.currentTimeMillis();
        for (int i = 0; i < N; ++i) {
            clientThreads[i] = new ClientThread(clients[i], latch, finishLatch);
            clientThreads[i].start();
        }
//        finishLatch.await();  // 异步通信需要等待消息回复
        latch.await();
        long total = System.currentTimeMillis() - beginTime;
        System.out.println("Total time:" + total);
        return total;
    }

    /**
     * 向客户端发送一条update指令进行测试
     *
     * @return 返回值时从发送到收到结果的时间
     * @throws Exception
     */
    public static long singleTest() throws Exception {
        long beginTime = System.currentTimeMillis();
        clients[0].commandServer(singleCommand);
        long total = System.currentTimeMillis() - beginTime;
        System.out.println("Total time: " + total);
        return total;
    }
}
