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

import moxy.impl.ConnectTo;
import moxy.impl.DispatchListener;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MoxyServer {
    private Log log = Log.get(getClass());
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

                ConnectTo connectTo = new ConnectTo(portToListenOn, socketAddress, dispatchListener);
                listenOnPortToRemote.put(portToListenOn, connectTo);

                if (started.get()) {
                    connectTo.startListenOn();
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
                info.getValue().startListenOn();
            }
            started.set(true);
        } catch (RuntimeException e) {
            stop();
            throw e;
        }
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

    public interface RouteTo {
        default void andConnectTo(String hostNameOrIpAddress, int portNumber) {
            andConnectTo(new InetSocketAddress(hostNameOrIpAddress, portNumber));
        }

        void andConnectTo(InetSocketAddress socketAddress);
    }
}
