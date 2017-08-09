import com.ele.me.CommonClient;

public class TestClient {
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
