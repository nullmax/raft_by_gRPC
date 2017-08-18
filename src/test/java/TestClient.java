import com.ele.me.ClientThread;
import com.ele.me.CommonClient;

import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicInteger;


public class TestClient {

    static AtomicInteger index = new AtomicInteger(0);
    static CommonClient[] clients = new CommonClient[5];

    public static void main(String[] args) throws Exception {
//        singleTest();

        for (int i = 0; i < 5; ++i)
            clients[i] = new CommonClient("localhost", 5500);

        for (int i = 0; i < 11; ++i)
            multiTest();
    }


    public static String getCommand(int id, int v) {
        return "INSERT INTO simple VALUES (" + id + "," + v + ")";
    }

    public static long multiTest() throws Exception {
        long beginTime = System.currentTimeMillis();
        ClientThread[] clientThreads = new ClientThread[5];

        CountDownLatch latch = new CountDownLatch(5);
        for (int i = 0; i < 5; ++i) {
            clientThreads[i] = new ClientThread(clients[i], latch);
            clientThreads[i].start();
        }
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
