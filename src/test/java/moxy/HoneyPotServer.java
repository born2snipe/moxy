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
import moxy.impl.ThreadKiller;
import org.junit.Assert;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static moxy.Log.Level.DEBUG;

public class HoneyPotServer {
    private final int port;
    private final LinkedList<String> outgoingData = new LinkedList<>();
    private final ArrayList<String> dataReceived = new ArrayList<>();
    private final Collection<Socket> sockets = new HashSet<>();
    private ConnectionAcceptorThread connectionAcceptorThread;
    private AtomicBoolean connectionMade = new AtomicBoolean(false);
    private CountDownLatch portBoundCountDown = new CountDownLatch(1);
    private Log log = new SysOutLog(DEBUG);
    private ExceptionHolder bindingException = new ExceptionHolder();
    private boolean started = false;

    public HoneyPotServer(int port) {
        this.port = port;
    }

    public void start() {
        log.debug("HONEY POT: starting...");
        connectionAcceptorThread = new ConnectionAcceptorThread("HONEY POT", port, log, new NewConnectionListener());
        connectionAcceptorThread.start();

        log.debug("HONEY POT: Waiting for port to bind...");
        waitForPortToBeBound();
        bindingException.reThrowAsNeeded();
        log.debug("HONEY POT: bound to port [" + port + "]");
        started = true;
    }

    public void sendData(String dataToSend) {
        synchronized (outgoingData) {
            outgoingData.add(dataToSend);
        }
    }

    public void stop() {
        if (started) {
            log.debug("HONEY POT: stopping...");
            ThreadKiller.killAndWait(connectionAcceptorThread);
            sockets.forEach((socket) -> {
                try {
                    log.debug("HONEY POT: Closing socket: " + socket);
                    socket.close();
                } catch (IOException e) {

                }
            });

            sockets.clear();
            log.debug("HONEY POT: stopped");
            started = false;
        }
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public void assertDataReceived(String expectedReceivedData) {
        RetryableAssertion assertion = new RetryableAssertion() {
            @Override
            protected void assertion() {
                synchronized (dataReceived) {
                    Assert.assertTrue("We have NOT received the data [" + expectedReceivedData + "]." +
                                    "\n\tWe have received: " + dataReceived,
                            dataReceived.contains(expectedReceivedData));
                }
            }
        };
        assertion.performAssertion();

    }

    private void waitForPortToBeBound() {
        try {
            portBoundCountDown.await();
        } catch (InterruptedException e) {

        }
    }

    public void assertSomeoneConnected() {
        RetryableAssertion assertion = new RetryableAssertion() {
            protected void assertion() {
                Assert.assertTrue("NO connection was ever made to the server", connectionMade.get());
            }
        };
        assertion.performAssertion();
    }

    public void assertDataNotReceived(String expectedDataToNotBeFound) {
        assertSomeoneConnected();
        synchronized (dataReceived) {
            Assert.assertFalse("We have received the data [" + expectedDataToNotBeFound + "].",
                    dataReceived.contains(expectedDataToNotBeFound));
        }
    }

    public void assertSomeDataWasReceived() {
        synchronized (dataReceived) {
            Assert.assertTrue("We never received any data", dataReceived.size() > 0);
        }
    }

    private class NewConnectionListener implements ConnectionAcceptorThread.Listener {

        public void newConnection(Socket socket) {
            ConsumeDataThread consumeDataThread = new ConsumeDataThread(socket, new ConsumeDataThread.Listener() {
                public void newDataReceived(Socket socket, byte[] data) {
                    synchronized (dataReceived) {
                        dataReceived.add(new String(data));
                    }
                }
            });
            SendOutgoingDataThread sendOutgoingDataThread = new SendOutgoingDataThread(socket);

            sockets.add(socket);
            connectionMade.set(true);
            consumeDataThread.start();
            sendOutgoingDataThread.start();
        }

        public void boundToLocalPort(int port) {
            portBoundCountDown.countDown();
        }

        public void failedToBindToPort(int port, BindException exception) {
            bindingException.holdOnTo(exception);
            portBoundCountDown.countDown();
        }
    }

    private class SendOutgoingDataThread extends Thread {
        private Socket socket;

        public SendOutgoingDataThread(Socket socket) {
            this.socket = socket;
            setDaemon(true);
        }

        public void run() {
            try (OutputStream outputStream = socket.getOutputStream()) {
                while (socket.isConnected()) {
                    synchronized (outgoingData) {
                        for (String data : outgoingData) {
                            outputStream.write(data.getBytes());
                        }
                        outputStream.flush();
                        outgoingData.clear();
                    }
                    pause();
                }
            } catch (IOException e) {
                log.error("A problem occurred trying to send data", e);
            }
        }

        private void pause() {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {

            }
        }
    }
}
