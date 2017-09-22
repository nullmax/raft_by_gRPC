import com.ele.me.RaftServer;

import java.util.ArrayList;

public class TestServer {
    static final int N = 3;

    public static void main(String[] args) throws Exception {
        ArrayList<String> addressList = new ArrayList<String>(N);
        ArrayList<Integer> portList = new ArrayList<>(N);

        for (int i = 0; i < N; ++i) {
//            addressList.add("10.101.35.37");
            addressList.add("localhost");
            portList.add(5500 + i);
        }

        RaftServer[] servers = new RaftServer[N];
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
