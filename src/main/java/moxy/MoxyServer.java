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
import java.util.concurrent.CountDownLatch;

public class MoxyServer {
    private Log log = new SysOutLog();
    private HashMap<Integer, ConnectTo> listenOnPortToRemote = new HashMap<>();

    public RouteTo listenOn(int portToListenOn) {
        return new RouteTo() {
            public void andConnectTo(String hostNameOrIpAddress, int portNumber) {
                listenOnPortToRemote.put(portToListenOn, new ConnectTo(hostNameOrIpAddress, portNumber));
            }
        };
    }

    public void start() {
        log.debug("Starting...");
        final CountDownLatch countDownLatch = new CountDownLatch(listenOnPortToRemote.size());
        for (Map.Entry<Integer, ConnectTo> info : listenOnPortToRemote.entrySet()) {
            ConnectionAcceptorThread connectionAcceptorThread = new ConnectionAcceptorThread(info.getKey(), log, new ConnectionAcceptorThread.Listener() {
                public void newConnection(Socket socket) throws IOException {
                    Socket to = new Socket();
                    to.setReuseAddress(true);
                    to.connect(new InetSocketAddress(info.getValue().host, info.getValue().port));
                    setupDataTransferringThreads(socket, to);
                }

                public void boundToLocalPort(int port) {
                    countDownLatch.countDown();
                }
            });
            connectionAcceptorThread.start();
            info.getValue().connectionAcceptorThread = connectionAcceptorThread;
        }

        try {
            log.debug("Waiting for all ports to be bound...");
            // block until all routes have attempted to bind to their ports
            countDownLatch.await();
            log.debug("All ports bound.");
        } catch (InterruptedException e) {

        }
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public void stop() {
        if (listenOnPortToRemote.size() > 0) {
            log.debug("Stopping all port listeners...");
            for (ConnectTo connectTo : listenOnPortToRemote.values()) {
                log.debug("Stop listening on port: " + connectTo.port);
                ThreadKiller.killAndWait(connectTo.connectionAcceptorThread);
            }
            listenOnPortToRemote.clear();
        }
    }

    public void stopListeningOn(int portNumber) throws IllegalArgumentException {
        log.debug("Stop listening on port: " + portNumber);
        ConnectTo connectTo = listenOnPortToRemote.remove(portNumber);
        if (connectTo != null) {
            ThreadKiller.killAndWait(connectTo.connectionAcceptorThread);
        } else {
            log.warn("Nothing is listening on port [" + portNumber + "]");
        }
    }

    private void setupDataTransferringThreads(Socket from, Socket to) throws IOException {
        new ReadAndSendDataThread(from, to, log).start();
        new ReadAndSendDataThread(to, from, log).start();
    }

    public interface RouteTo {
        void andConnectTo(String hostNameOrIpAddress, int portNumber);
    }

    private class ConnectTo {
        private String host;
        private int port;
        private ConnectionAcceptorThread connectionAcceptorThread;

        public ConnectTo(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
