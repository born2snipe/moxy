/**
 * Copyright to the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package moxy;

import moxy.impl.ConnectionAcceptorThread;
import moxy.impl.ReadAndSendDataThread;
import moxy.impl.ThreadKiller;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class MoxyServer {
    private HashMap<Integer, ConnectTo> listenOnPortToRemote = new HashMap<>();
    private ConnectionAcceptorThread connectionAcceptorThread;

    public RouteTo listenOn(int portToListenOn) {
        return new RouteTo() {
            public void andConnectTo(String hostNameOrIpAddress, int portNumber) {
                listenOnPortToRemote.put(portToListenOn, new ConnectTo(hostNameOrIpAddress, portNumber));
            }
        };
    }

    public void start() {
        final Map.Entry<Integer, ConnectTo> info = listenOnPortToRemote.entrySet().iterator().next();
        connectionAcceptorThread = new ConnectionAcceptorThread(info.getKey(), new ConnectionAcceptorThread.Listener() {
            public void newConnection(Socket socket) throws IOException {
                Socket to = new Socket();
                to.setReuseAddress(true);
                to.connect(new InetSocketAddress(info.getValue().host, info.getValue().port));
                setupDataTransferringThreads(socket, to);
            }
        });
        connectionAcceptorThread.start();
    }

    private void setupDataTransferringThreads(Socket from, Socket to) throws IOException {
        new ReadAndSendDataThread(from, to).start();
        new ReadAndSendDataThread(to, from).start();
    }

    public void stop() {
        ThreadKiller.killAndWait(connectionAcceptorThread);
    }

    public interface RouteTo {
        void andConnectTo(String hostNameOrIpAddress, int portNumber);
    }

    private class ConnectTo {
        private String host;
        private int port;

        public ConnectTo(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
