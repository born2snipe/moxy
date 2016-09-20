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
package moxy.impl;

import moxy.Log;
import moxy.MoxyListener;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

public class ConnectTo {
    private static final Log LOG = Log.get(ConnectTo.class);
    public final InetSocketAddress socketAddress;
    private final int portToListenOn;
    private ArrayList<Thread> threads = new ArrayList<>();
    // todo - need to find a way to get these to auto cleanup on death
    private ArrayList<RelayInfo> relayInfos = new ArrayList<>();
    private MoxyListener moxyListener;

    public ConnectTo(int portToListenOn, InetSocketAddress socketAddress, MoxyListener moxyListener) {
        this.portToListenOn = portToListenOn;
        this.socketAddress = socketAddress;
        this.moxyListener = moxyListener;
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

    public void startListenOn() {
        final ExceptionHolder exceptionHolder = new ExceptionHolder();
        final CountDownLatch portBindingLatch = new CountDownLatch(1);
        LOG.debug("Setup listening route: localhost:" + portToListenOn + " -> " + socketAddress);
        ConnectionAcceptorThread connectionAcceptorThread = new ConnectionAcceptorThread("MOXY", portToListenOn, new ConnectionAcceptorThread.Listener() {
            public void newConnection(Socket listener) throws IOException {
                try {
                    moxyListener.connectionMade(portToListenOn, socketAddress);
                    Socket routeTo = new Socket();
                    routeTo.setReuseAddress(true);
                    routeTo.connect(socketAddress);
                    startReadingAndWriting(listener, routeTo, moxyListener);
                } catch (IOException e) {
                    LOG.error("Failed to connect to route server: " + socketAddress, e);
                }
            }

            public void boundToLocalPort(int port) {
                LOG.debug("Port [" + port + "] bound!");
                portBindingLatch.countDown();
            }

            public void failedToBindToPort(int port, BindException exception) {
                LOG.debug("Port [" + port + "] failed to bind!");
                exceptionHolder.holdOnTo(new IllegalStateException("Failed to bind to port [" + port + "]", exception));
                portBindingLatch.countDown();
            }
        });

        associateThread(connectionAcceptorThread).start();

        try {
            LOG.debug("Waiting for port [" + portToListenOn + "] to bind...");
            portBindingLatch.await();

        } catch (InterruptedException e) {

        }

        exceptionHolder.reThrowAsNeeded();
    }

    private void startReadingAndWriting(Socket listener, Socket routeTo, MoxyListener dispatchListener) {
        RelayInfo relayInfo = new RelayInfo(listener, routeTo);
        relayInfo.startRelaying(dispatchListener);
        relayInfos.add(relayInfo);
    }

    private Thread associateThread(Thread thread) {
        threads.add(thread);
        return thread;
    }
}
