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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import static moxy.Log.Level.DEBUG;
import static moxy.SocketUtil.attemptToBindTo;
import static moxy.SocketUtil.connectToAndSend;
import static org.junit.Assert.fail;

public class MoxyServerTest {
    private MoxyServer moxyServer;
    private HoneyPotServer honeyPotServer;

    @Before
    public void setUp() throws Exception {
        honeyPotServer = new HoneyPotServer(9090);
        honeyPotServer.start();

        moxyServer = new MoxyServer();
        moxyServer.setLog(new SysOutLog(DEBUG));
    }

    @After
    public void tearDown() throws Exception {
        honeyPotServer.stop();
        moxyServer.stop();
    }

    @Test
    public void shouldAllowProvidingAInetSocketAddressForTheRoute() throws IOException, InterruptedException {
        moxyServer.listenOn(7878).andConnectTo(new InetSocketAddress("localhost", 9090));
        moxyServer.start();

        connectToMoxyAndSend(7878, "Hello World");
        honeyPotServer.assertDataReceived("Hello World");
    }

    @Test
    public void shouldCloseAllTheServerSocketsWhenStopIsCalled() throws IOException, InterruptedException {
        moxyServer.listenOn(7878).andConnectTo("localhost", 9090);
        moxyServer.listenOn(8080).andConnectTo("localhost", 9090);
        moxyServer.start();
        moxyServer.stop();

        attemptToBindTo(7878);
        attemptToBindTo(8080);
    }

    @Test
    public void shouldCloseTheServerSocketWhenStopIsCalled() throws IOException, InterruptedException {
        moxyServer.listenOn(7878).andConnectTo("localhost", 9090);
        moxyServer.start();
        moxyServer.stop();

        attemptToBindTo(7878);
    }

    @Test
    public void shouldNotBlowUpIfNoRoutesHaveBeenDefinedAtStartUpTime() {
        moxyServer.start();
    }

    @Test
    public void shouldAllowSettingUpARouteAndConnectingToTheRemoteServerAndReceivingData() {
        moxyServer.listenOn(7878).andConnectTo("localhost", 9090);
        moxyServer.start();

        honeyPotServer.sendData("From Remote");

        connectToMoxyAndWaitForData(7878, "From Remote");
    }

    @Test
    public void shouldAllowSettingUpARouteAndConnectingToTheRemoteServerAndSendingData() {
        moxyServer.listenOn(7878).andConnectTo("localhost", 9090);
        moxyServer.start();

        connectToMoxyAndSend(7878, "Hello World");

        honeyPotServer.assertSomeoneConnected();
        honeyPotServer.assertDataReceived("Hello World");
    }

    @Test
    public void shouldAllowAddingMultipeRoutes() {
        HoneyPotServer otherHoneyPot = new HoneyPotServer(9999);
        otherHoneyPot.start();

        moxyServer.listenOn(7878).andConnectTo("localhost", 9090);
        moxyServer.listenOn(7979).andConnectTo("localhost", 9999);
        moxyServer.start();

        connectToMoxyAndSend(7878, "Hello World");
        connectToMoxyAndSend(7979, "Good bye");

        honeyPotServer.assertDataReceived("Hello World");
        honeyPotServer.assertDataNotReceived("Good bye");
        otherHoneyPot.assertDataReceived("Good bye");
        otherHoneyPot.assertDataNotReceived("Hello World");
    }

    @Test
    public void shouldAllowStopListeningOnAPort() {
        moxyServer.listenOn(7878).andConnectTo("localhost", 9090);
        moxyServer.start();
        moxyServer.stopListeningOn(7878);

        attemptToBindTo(7878);
    }

    @Test
    public void shouldAllowAddingListenersWhileTheServerIsRunning() {
        moxyServer.start();
        moxyServer.listenOn(7878).andConnectTo("localhost", 9090);

        connectToMoxyAndSend(7878, "Hello World");

        honeyPotServer.assertDataReceived("Hello World");
    }

    @Test
    public void shouldNotBlowUpIfTheServerHasAlreadyBeenStarted() {
        moxyServer.listenOn(7878).andConnectTo("localhost", 9090);
        moxyServer.start();
        moxyServer.start();

        connectToMoxyAndSend(7878, "Hello World");

        honeyPotServer.assertDataReceived("Hello World");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldBlowUpIfYouAttemptToListenOnTheSamePortMultipleTimes() {
        moxyServer.listenOn(7878).andConnectTo("localhost", 9090);
        moxyServer.listenOn(7878).andConnectTo("localhost", 8080);
    }

    @Test
    public void shouldBeAbleToRestartTheServerAndYourRoutesShouldBeAvailable() {
        moxyServer.listenOn(7878).andConnectTo("localhost", 9090);
        moxyServer.start();

        moxyServer.stop();
        moxyServer.start();

        connectToMoxyAndSend(7878, "Hello World");
        honeyPotServer.assertDataReceived("Hello World");
    }

    @Test
    public void shouldBlowUpIfAPortIsAlreadyInUse() {
        moxyServer.listenOn(9090).andConnectTo("localhost", 8080);
        try {
            moxyServer.start();
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @Test
    public void shouldCleanUpAnySuccessfulBindingsIfALaterBindingFails() {
        moxyServer.listenOn(7878).andConnectTo("localhost", 9090);
        moxyServer.listenOn(7979).andConnectTo("localhost", 9090);
        moxyServer.listenOn(9090).andConnectTo("localhost", 9090);

        try {
            moxyServer.start();
            fail();
        } catch (IllegalStateException e) {

        }

        attemptToBindTo(7878);
        attemptToBindTo(7979);
    }

    @Test
    public void shouldBlowUpIfAPortIsAlreadyInUseWhileTheServerIsAlreadyRunning() {
        moxyServer.listenOn(7878).andConnectTo("localhost", 9090);
        moxyServer.start();

        try {
            moxyServer.listenOn(9090).andConnectTo("localhost", 9090);
            fail();
        } catch (IllegalStateException e) {

        }

        connectToMoxyAndSend(7878, "Hello World");
        honeyPotServer.assertDataReceived("Hello World");
    }

    private void connectToMoxyAndWaitForData(int portToConnectTo, String expectedData) {
        try (Socket socket = new Socket()) {
            socket.setReuseAddress(true);
            socket.setSoTimeout(1000);
            socket.connect(new InetSocketAddress("localhost", portToConnectTo));

            AtomicBoolean waitingForData = new AtomicBoolean(true);
            new ConsumeDataThread(socket, new ConsumeDataThread.Listener() {
                public void newDataReceived(Socket socket, byte[] data) {
                    waitingForData.set(false);
                    Assert.assertEquals(expectedData, new String(data));
                }
            }).start();

            long start = System.currentTimeMillis();
            while (waitingForData.get()) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException e) {

                }
                if (System.currentTimeMillis() - start > 2000) {
                    fail();
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void connectToMoxyAndSend(int portToConnectTo, String dataToSend) {
        connectToAndSend("localhost", portToConnectTo, dataToSend);
    }
}