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
import moxy.impl.DispatchListener;
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
    private DispatchListener dispatchListener = new DispatchListener();

    /**
     * Provide what local port you would like to listen on
     *
     * @param portToListenOn - the local port number to bind to and begin listening for incoming connections
     * @return the instance of the RouteTo to tell where to route the traffic
     */
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

    /**
     * Tell the server to bind and start listening for incoming connections
     */
    public void start() {
        if (started.get()) {
            log.warn("Server already started");
            return;
        }

        log.info("Starting...");
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

    public void setLog(Log log) {
        this.log = log;
    }

    /**
     * Tell the server to disconnect any connections and stop listening for any new connections
     */
    public void stop() {
        log.info("Stopping all port listeners...");
        listenOnPortToRemote.values().forEach(ConnectTo::shutdown);
        started.set(false);
    }

    /**
     * Tell the server to stop listening on the provided port, but do not remove the routing configuration
     * <p>
     * Note: this is very similar to removeListenerOn(...), but the route configuration will live through a server restart
     *
     * @param portNumber - the local port you would like to stop listening for incoming connections
     */
    public void stopListeningOn(int portNumber) {
        log.info("Stop listening on port: " + portNumber);
        ConnectTo connectTo = listenOnPortToRemote.get(portNumber);
        if (connectTo != null) {
            connectTo.shutdown();
        } else {
            log.warn("Nothing is listening on port [" + portNumber + "]");
        }
    }

    /**
     * Tell the server to stop listening on the provided port and remove the routing information also
     * <p>
     * Note: this is very similar to stopListeningOn(...), but the route configuration will NOT be available if you choose to restart the server
     *
     * @param portNumber - the local port you would like to stop listening for incoming connections
     */
    public void removeListenerOn(int portNumber) {
        log.info("Removing listener on port: " + portNumber);
        stopListeningOn(portNumber);
        listenOnPortToRemote.remove(portNumber);
    }

    /**
     * Add a listener to get notified when certain events happen
     *
     * @param listener - the instance of the listener to be registered
     */
    public void addListener(MoxyListener listener) {
        dispatchListener.addListener(listener);
    }

    private void assertPortIsNotAlreadySetup(int portToListenOn) {
        if (listenOnPortToRemote.containsKey(portToListenOn)) {
            throw new IllegalArgumentException("There can only be one route for a single port number. It appears port number [" + portToListenOn + "] is already setup.");
        }
    }

    private void startListeningOn(Integer port, final ConnectTo connectTo) {
        final ExceptionHolder exceptionHolder = new ExceptionHolder();
        final CountDownLatch portBindingLatch = new CountDownLatch(1);
        log.debug("Setup listening route: localhost:" + port + " -> " + connectTo.socketAddress);
        ConnectionAcceptorThread connectionAcceptorThread = new ConnectionAcceptorThread("MOXY", port, log, new ConnectionAcceptorThread.Listener() {
            public void newConnection(Socket listener) throws IOException {
                try {
                    dispatchListener.connectionMade(port, connectTo.socketAddress);
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
