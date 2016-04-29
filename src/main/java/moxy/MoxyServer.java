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
                assertPortIsNotAlreadySetup(portToListenOn);

                ConnectTo connectTo = new ConnectTo(hostNameOrIpAddress, portNumber);
                listenOnPortToRemote.put(portToListenOn, connectTo);

                if (started.get()) {
                    startListeningOn(portToListenOn, connectTo);
                }
            }
        };
    }

    private void assertPortIsNotAlreadySetup(int portToListenOn) {
        if (listenOnPortToRemote.containsKey(portToListenOn)) {
            throw new IllegalArgumentException("There can only be one route for a single port number. It appears port number [" + portToListenOn + "] is already setup.");
        }
    }

    public void start() {
        if (started.get()) {
            log.warn("Server already started");
            return;
        }

        log.debug("Starting...");
        for (Map.Entry<Integer, ConnectTo> info : listenOnPortToRemote.entrySet()) {
            startListeningOn(info.getKey(), info.getValue());
        }
        started.set(true);
    }

    private void startListeningOn(Integer port, final ConnectTo connectTo) {
        final CountDownLatch portBindingLatch = new CountDownLatch(1);
        log.debug("Setup listening route: localhost:" + port + " -> " + connectTo.host + ":" + connectTo.port);
        ConnectionAcceptorThread connectionAcceptorThread = new ConnectionAcceptorThread(port, log, new ConnectionAcceptorThread.Listener() {
            public void newConnection(Socket socket) throws IOException {
                Socket to = new Socket();
                to.setReuseAddress(true);
                to.connect(new InetSocketAddress(connectTo.host, connectTo.port));
                setupDataTransferringThreads(socket, to);
            }

            public void boundToLocalPort(int port) {
                portBindingLatch.countDown();
            }
        });

        connectionAcceptorThread.start();
        connectTo.connectionAcceptorThread = connectionAcceptorThread;

        try {
            log.debug("Waiting for port [" + port + "] to bind...");
            // wait for port to be bound
            portBindingLatch.await();
            log.debug("Port [" + port + "] bound!");
        } catch (InterruptedException e) {

        }
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public void stop() {
        log.debug("Stopping all port listeners...");
        for (ConnectTo connectTo : listenOnPortToRemote.values()) {
            connectTo.shutdown();
        }
        started.set(false);
    }

    public void stopListeningOn(int portNumber) {
        log.debug("Stop listening on port: " + portNumber);
        ConnectTo connectTo = listenOnPortToRemote.get(portNumber);
        if (connectTo != null) {
            connectTo.shutdown();
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

        public void shutdown() {
            if (connectionAcceptorThread != null) {
                log.debug("Stop listening on port: " + port);
                ThreadKiller.killAndWait(connectionAcceptorThread);
                connectionAcceptorThread = null;
            }
        }
    }
}
