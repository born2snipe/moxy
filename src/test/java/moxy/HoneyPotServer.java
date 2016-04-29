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
import moxy.impl.ThreadKiller;
import org.junit.Assert;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static moxy.Log.Level.DEBUG;

public class HoneyPotServer {
    private final int port;
    private final LinkedList<String> outgoingData = new LinkedList<>();
    private ArrayList<String> dataReceived = new ArrayList<>();
    private ConnectionAcceptorThread connectionAcceptorThread;
    private AtomicBoolean connectionMade = new AtomicBoolean(false);
    private CountDownLatch portBoundCountDown = new CountDownLatch(1);
    private SysOutLog log = new SysOutLog(DEBUG);

    public HoneyPotServer(int port) {
        this.port = port;
    }

    public void start() {
        log.debug("HONEY POT: starting...");
        connectionAcceptorThread = new ConnectionAcceptorThread(port, log, new NewConnectionListener());
        connectionAcceptorThread.start();

        waitForPortToBeBound();
        log.debug("HONEY POT: bound to port [" + port + "]");
    }

    public void sendData(String dataToSend) {
        synchronized (outgoingData) {
            outgoingData.add(dataToSend);
        }
    }

    public void stop() {
        log.debug("HONEY POT: stopping...");
        ThreadKiller.killAndWait(connectionAcceptorThread);
        log.debug("HONEY POT: stopped");
    }

    public void assertDataReceived(String expectedReceivedData) {
        RetryableAssertion assertion = new RetryableAssertion() {
            @Override
            protected void assertion() {
                Assert.assertTrue("We have NOT received the data [" + expectedReceivedData + "]." +
                                "\n\tWe have received: " + dataReceived,
                        dataReceived.contains(expectedReceivedData));
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
        Assert.assertFalse("We have received the data [" + expectedDataToNotBeFound + "].",
                dataReceived.contains(expectedDataToNotBeFound));
    }

    private class NewConnectionListener implements ConnectionAcceptorThread.Listener {

        public void newConnection(Socket socket) {
            connectionMade.set(true);
            ConsumeDataThread consumeDataThread = new ConsumeDataThread(socket, new ConsumeDataThread.Listener() {
                public void newDataReceived(Socket socket, byte[] data) {
                    dataReceived.add(new String(data));
                }
            });
            consumeDataThread.start();
            new SendOutgoingDataThread(socket).start();
        }

        public void boundToLocalPort(int port) {
            portBoundCountDown.countDown();
        }

        public void failedToBindToPort(int port, BindException exception) {
            portBoundCountDown.countDown();
            exception.printStackTrace();
        }
    }

    private class SendOutgoingDataThread extends Thread {
        private Socket socket;

        public SendOutgoingDataThread(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try (OutputStream outputStream = socket.getOutputStream()) {
                while (true) {
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
