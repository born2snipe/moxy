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
import moxy.impl.ThreadKiller;
import org.junit.Assert;

import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class HoneyPotServer {
    private final int port;
    private ArrayList<String> dataReceived = new ArrayList<>();
    private ConnectionAcceptorThread connectionAcceptorThread;
    private ConsumeDataThread consumeDataThread;
    private AtomicBoolean connectionMade = new AtomicBoolean(false);

    public HoneyPotServer(int port) {
        this.port = port;
    }

    public void start() {
        connectionAcceptorThread = new ConnectionAcceptorThread(port, new NewConnectionListener());
        connectionAcceptorThread.start();
    }

    public void stop() {
        ThreadKiller.killAndWait(connectionAcceptorThread);
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

    public void assertSomeoneConnected() {
        RetryableAssertion assertion = new RetryableAssertion() {
            @Override
            protected void assertion() {
                Assert.assertTrue("NO connection was ever made to the server", connectionMade.get());
            }
        };
        assertion.performAssertion();
    }

    private class NewConnectionListener implements ConnectionAcceptorThread.Listener {
        public void newConnection(Socket socket) {
            connectionMade.set(true);
            consumeDataThread = new ConsumeDataThread(socket, new ConsumeDataThread.Listener() {
                @Override
                public void newDataReceived(Socket socket, byte[] data) {
                    dataReceived.add(new String(data));
                }
            });
            consumeDataThread.start();
        }
    }
}
