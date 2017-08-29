import com.ele.me.CommonClient;
import com.ele.me.RaftServer;

import java.util.ArrayList;

public class TestServer {
    static final int N = 5;

    public static void main(String[] args) throws Exception {
        ArrayList<String> addressList = new ArrayList<String>(N);
        ArrayList<Integer> portList = new ArrayList<>(N);

        for (int i = 0; i < N; ++i) {
            addressList.add("localhost");
            portList.add(5500 + i);
        }

        RaftServer[] servers = new RaftServer[5];
        for (int i = 0; i < servers.length; ++i) {
            servers[i] = new RaftServer(portList.get(i), i);
            servers[i].setCommunicateList(addressList, portList);
            servers[i].start();
        }

        for (int i = 0; i < servers.length; ++i) {
            servers[i].blockUntilShutdown();
        }
    }
}
