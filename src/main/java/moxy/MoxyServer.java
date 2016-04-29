/**
 * Copyright to the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class MoxyServer {
    private Log log = new SysOutLog();
    private AtomicBoolean started = new AtomicBoolean(false);
    private Map<Integer, ConnectTo> listenOnPortToRemote = Collections.synchronizedMap(new HashMap<>());

    public RouteTo listenOn(int portToListenOn) {
        return new RouteTo() {
            public void andConnectTo(String hostNameOrIpAddress, int portNumber) {
                ConnectTo connectTo = new ConnectTo(hostNameOrIpAddress, portNumber);
                listenOnPortToRemote.put(portToListenOn, connectTo);
                if (started.get()) {
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    startListeningOn(portToListenOn, connectTo, countDownLatch);

                    try {
                        log.debug("Waiting for port to be bound...");
                        countDownLatch.await();
                    } catch (InterruptedException e) {

                    }
                }
            }
        };
    }

    public void start() {
        if (started.get()) {
            log.warn("Server already started");
            return;
        }

        log.debug("Starting...");
        final CountDownLatch countDownLatch = new CountDownLatch(listenOnPortToRemote.size());
        for (Map.Entry<Integer, ConnectTo> info : listenOnPortToRemote.entrySet()) {
            startListeningOn(info.getKey(), info.getValue(), countDownLatch);
        }

        try {
            log.debug("Waiting for all ports to be bound...");
            // block until all routes have attempted to bind to their ports
            countDownLatch.await();
            log.debug("All ports bound.");
        } catch (InterruptedException e) {

        }

        started.set(true);
    }

    private void startListeningOn(Integer port, final ConnectTo connectTo, final CountDownLatch countDownLatch) {
        log.debug("Setup listening route: localhost:" + port + " -> " + connectTo.host + ":" + connectTo.port);
        ConnectionAcceptorThread connectionAcceptorThread = new ConnectionAcceptorThread(port, log, new ConnectionAcceptorThread.Listener() {
            public void newConnection(Socket socket) throws IOException {
                Socket to = new Socket();
                to.setReuseAddress(true);
                to.connect(new InetSocketAddress(connectTo.host, connectTo.port));
                setupDataTransferringThreads(socket, to);
            }

            public void boundToLocalPort(int port) {
                countDownLatch.countDown();
            }
        });
        connectionAcceptorThread.start();
        connectTo.connectionAcceptorThread = connectionAcceptorThread;
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

    public void stopListeningOn(int portNumber) {
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
