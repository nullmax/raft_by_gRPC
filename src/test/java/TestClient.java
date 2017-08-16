import com.ele.me.CommonClient;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;


public class TestClient {

    static AtomicInteger index = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {

        for (int i = 0; i < 5; ++i) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    CommonClient commonClient = new CommonClient("localhost", 5500);
                    String command;
                    for (int j = 0; j < 50; ++j) { //todo 总数暂时不要超过200
                        command = getCommand(index.getAndIncrement(), commonClient.id);
                        commonClient.commandServer(command);
                    }
                    try {
                        commonClient.shutdown();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();
        }
    }


    public static String getCommand(int id, int v) {
        return "INSERT INTO simple VALUES (" + id + "," + v + ")";
    }

    public static void lastTest() throws Exception {
        CommonClient client = new CommonClient("localhost", 5500);
        String command = "INSERT INTO simple VALUES (1, 9)";
        try {
//            command = "DELETE FROM simple";
//            client.commandServer(command);
//            command = "INSERT INTO simple VALUES (1, 9)";
//            client.commandServer(command);
//            command = "INSERT INTO simple VALUES (2, 8)";
//            client.commandServer(command);
//            String query = "SELECT * FROM simple";
//            client.queryServer(query);

            while (true) {
                Scanner scanner = new Scanner(System.in);
                System.out.print("Command: ");
                command = scanner.nextLine();
                client.commandServer(command);
            }
        } finally {
            client.shutdown();
        }
    }
}

class TestThread extends Thread {
    CommonClient commonClient;
    String command;

    TestThread() {
        commonClient = new CommonClient("localhost", 5500);
        start();
    }

    @Override
    public void run() {
        super.run();
    }
}