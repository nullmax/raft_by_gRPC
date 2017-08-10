import com.ele.me.CommonClient;

import java.util.Scanner;

public class TestClient {
    public static void main(String[] args) throws Exception {
        CommonClient client = new CommonClient("localhost", 5500, 10);
        String command;
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
