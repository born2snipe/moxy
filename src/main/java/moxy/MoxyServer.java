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
import moxy.impl.ExceptionHolder;
import moxy.impl.ReadAndSendDataThread;
import moxy.impl.ThreadKiller;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class MoxyServer {
    private Log log = new SysOutLog();
    private AtomicBoolean started = new AtomicBoolean(false);
    private Map<Integer, ConnectTo> listenOnPortToRemote = Collections.synchronizedMap(new LinkedHashMap<>());

    public RouteTo listenOn(int portToListenOn) {
        return new RouteTo() {
            public void andConnectTo(InetSocketAddress socketAddress) {
                assertPortIsNotAlreadySetup(portToListenOn);

                ConnectTo connectTo = new ConnectTo(socketAddress);
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
        try {
            for (Map.Entry<Integer, ConnectTo> info : listenOnPortToRemote.entrySet()) {
                startListeningOn(info.getKey(), info.getValue());
            }
            started.set(true);
        } catch (RuntimeException e) {
            stop();
            throw e;
        }
    }

    private void startListeningOn(Integer port, final ConnectTo connectTo) {
        final ExceptionHolder exceptionHolder = new ExceptionHolder();
        final CountDownLatch portBindingLatch = new CountDownLatch(1);
        log.debug("Setup listening route: localhost:" + port + " -> " + connectTo.socketAddress);
        ConnectionAcceptorThread connectionAcceptorThread = new ConnectionAcceptorThread("MOXY", port, log, new ConnectionAcceptorThread.Listener() {
            public void newConnection(Socket listener) throws IOException {
                try {
                    Socket routeTo = new Socket();
                    routeTo.setReuseAddress(true);
                    routeTo.connect(connectTo.socketAddress);
                    connectTo.startReadingAndWriting(listener, routeTo);
                } catch (IOException e) {
                    log.error("Failed to connect to route server: " + connectTo.socketAddress, e);
                }
            }

            public void boundToLocalPort(int port) {
                log.debug("Port [" + port + "] bound!");
                portBindingLatch.countDown();
            }

            public void failedToBindToPort(int port, BindException exception) {
                log.debug("Port [" + port + "] failed to bind!");
                exceptionHolder.holdOnTo(new IllegalStateException("Failed to bind to port [" + port + "]", exception));
                portBindingLatch.countDown();
            }
        });

        connectTo.associateThread(connectionAcceptorThread).start();

        try {
            log.debug("Waiting for port [" + port + "] to bind...");
            portBindingLatch.await();

        } catch (InterruptedException e) {

        }

        exceptionHolder.reThrowAsNeeded();
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public void stop() {
        log.debug("Stopping all port listeners...");
        listenOnPortToRemote.values().forEach(ConnectTo::shutdown);
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

    public void removeListenerOn(int portNumber) {
        log.debug("Removing listener on port: " + portNumber);
        stopListeningOn(portNumber);
        listenOnPortToRemote.remove(portNumber);
    }

    public interface RouteTo {
        default void andConnectTo(String hostNameOrIpAddress, int portNumber) {
            andConnectTo(new InetSocketAddress(hostNameOrIpAddress, portNumber));
        }

        void andConnectTo(InetSocketAddress socketAddress);
    }

    private class ConnectTo {
        private final InetSocketAddress socketAddress;
        private ArrayList<Thread> threads = new ArrayList<>();
        // todo - need to find a way to get these to auto cleanup on death
        private ArrayList<RelayInfo> relayInfos = new ArrayList<>();

        public ConnectTo(InetSocketAddress socketAddress) {
            this.socketAddress = socketAddress;
        }

        public Thread associateThread(Thread thread) {
            threads.add(thread);
            return thread;
        }

        public void shutdown() {
            if (threads.size() > 0) {
                threads.forEach(ThreadKiller::killAndWait);
                threads.clear();
            }

            for (RelayInfo relayInfo : relayInfos) {
                relayInfo.stopRelaying();
            }
            relayInfos.clear();
        }

        public void startReadingAndWriting(Socket listener, Socket routeTo) {
            RelayInfo relayInfo = new RelayInfo(listener, routeTo);
            relayInfo.startRelaying();
            relayInfos.add(relayInfo);
        }
    }

    private class RelayInfo {
        private Socket listener;
        private Socket routeTo;
        private ArrayList<Thread> threads = new ArrayList<>();

        public RelayInfo(Socket listener, Socket routeTo) {
            this.listener = listener;
            this.routeTo = routeTo;
        }

        public void startRelaying() {
            ReadAndSendDataThread listenerToRouteTo = new ReadAndSendDataThread(listener, routeTo, log);
            listenerToRouteTo.start();
            ReadAndSendDataThread routeToToListener = new ReadAndSendDataThread(routeTo, listener, log);
            routeToToListener.start();

            threads.add(listenerToRouteTo);
            threads.add(routeToToListener);
        }

        public void stopRelaying() {
            for (Thread thread : threads) {
                ThreadKiller.killAndWait(thread);
            }
        }
    }
}
