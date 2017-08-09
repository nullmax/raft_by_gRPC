import com.ele.me.CommonClient;
import com.ele.me.RaftServer;

public class TestServer {
    public static void main(String[] args) throws Exception {
        int port = 5000;
        if (args.length > 0)
            port = Integer.parseInt(args[0]);
        RaftServer[] servers = new RaftServer[5];
        for (int i = 0; i < servers.length; ++i) {
            servers[i] = new RaftServer(port);
            servers[i].start();
        }

        for (int i = 0; i < servers.length; ++i)
            servers[i].blockUntilShutdown();


    }
}
