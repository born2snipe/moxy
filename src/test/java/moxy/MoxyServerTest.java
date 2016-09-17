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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static moxy.Log.Level.OFF;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class MoxyServerTest {
    private static final int HONEY_POT_PORT = 19090;
    private MoxyServer moxyServer;
    private HoneyPotServer honeyPotServer;
    private SysOutLog log;
    private HashSet<HoneyPotServer> honeyPots = new HashSet<>();

    @Before
    public void setUp() throws Exception {
        log = new SysOutLog(OFF);

        honeyPotServer = startNewHoneyPot(HONEY_POT_PORT);

        moxyServer = new MoxyServer();
        moxyServer.setLog(log);
    }

    @After
    public void tearDown() throws Exception {
        honeyPots.forEach(HoneyPotServer::stop);
        moxyServer.stop();
    }

    @Test
    public void shouldAllowListeningForNewConnections() {
        AssertableListener assertableListener = new AssertableListener();

        moxyServer.listenOn(9999).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.listenOn(9998).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.addListener(assertableListener);
        moxyServer.start();

        connectToAndSend(9999, "Hello");
        moxyServer.stop();

        assertableListener.assertConnectionWasMadeOn(9999);
        assertableListener.assertNoConnectionWasMadeOn(9998);
    }

    @Test
    public void shouldKillBothConnectionsIfTheRemoteServersConnectionWasClosed() throws InterruptedException {
        moxyServer.listenOn(9999).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.start();

        AlwaysStreamingDataThread alwaysStreamingThread = new AlwaysStreamingDataThread("localhost", 9999);
        alwaysStreamingThread.start();

        honeyPotServer.stop();
        alwaysStreamingThread.join();

        alwaysStreamingThread.assertSocketClosed();
    }

    @Test
    public void shouldKillRemoteConnectionsOnShutdownEvenIfTheyAreStillWritingData() throws InterruptedException {
        moxyServer.listenOn(9999).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.start();

        AlwaysStreamingDataThread alwaysStreamingThread = new AlwaysStreamingDataThread("localhost", 9999);
        alwaysStreamingThread.start();

        moxyServer.stop();
        alwaysStreamingThread.join();

        honeyPotServer.assertSomeoneConnected();
        honeyPotServer.assertSomeDataWasReceived();
        alwaysStreamingThread.assertSocketClosed();
    }

    @Test
    public void shouldBeAbleToConnectToTheRouteServerOnceItBecomesAvailable() {
        moxyServer.listenOn(7878).andConnectTo("localhost", 9999);
        moxyServer.start();

        connectToAndSend(7878, "Hello World");

        HoneyPotServer honeyPotServer = startNewHoneyPot(9999);

        connectToAndSend(7878, "Hello Again");

        honeyPotServer.assertSomeoneConnected();
        honeyPotServer.assertDataNotReceived("Hello World");
        honeyPotServer.assertDataReceived("Hello Again");
    }

    @Test
    public void shouldNotKillTheListenerThreadWhenTheRouteServerIsUnavailable() {
        moxyServer.listenOn(7878).andConnectTo("localhost", 9999);
        moxyServer.start();

        connectToAndSend(7878, "Hello World");

        AssertPort.assertPortIsInUse(7878);
    }

    @Test
    public void shouldAllowMultipleRoutesToTheSameServer() {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.listenOn(7979).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.start();

        connectToAndSend(7878, "Hello World");
        connectToAndSend(7979, "Good bye");

        honeyPotServer.assertDataReceived("Hello World");
        honeyPotServer.assertDataReceived("Good bye");
    }

    @Test
    public void shouldBeAbleToRemoveARouteWhileTheServerIsRunning() {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.start();

        moxyServer.removeListenerOn(7878);

        AssertPort.assertPortIsAvailable(7878);
    }

    @Test
    public void shouldBeAbleToRemoveARoute() {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.removeListenerOn(7878);
        moxyServer.start();

        AssertPort.assertPortIsAvailable(7878);
    }

    @Test
    public void shouldSupportMultipleConnectionsToTheSameListeningPort() {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.start();

        connectToAndSend(7878, "Hello World");
        connectToAndSend(7878, "Hello Again");
        honeyPotServer.assertDataReceived("Hello World");
        honeyPotServer.assertDataReceived("Hello Again");
    }

    @Test
    public void shouldAllowProvidingAInetSocketAddressForTheRoute() throws IOException, InterruptedException {
        moxyServer.listenOn(7878).andConnectTo(new InetSocketAddress("localhost", HONEY_POT_PORT));
        moxyServer.start();

        connectToAndSend(7878, "Hello World");
        honeyPotServer.assertDataReceived("Hello World");
    }

    @Test
    public void shouldCloseAllTheServerSocketsWhenStopIsCalled() throws IOException, InterruptedException {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.listenOn(8080).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.start();
        moxyServer.stop();

        AssertPort.assertPortIsAvailable(7878);
        AssertPort.assertPortIsAvailable(8080);
    }

    @Test
    public void shouldCloseTheServerSocketWhenStopIsCalled() throws IOException, InterruptedException {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.start();
        moxyServer.stop();

        AssertPort.assertPortIsAvailable(7878);
    }

    @Test
    public void shouldNotBlowUpIfNoRoutesHaveBeenDefinedAtStartUpTime() {
        moxyServer.start();
    }

    @Test
    public void shouldAllowSettingUpARouteAndConnectingToTheRemoteServerAndReceivingData() {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.start();

        honeyPotServer.sendData("From Remote");

        connectToMoxyAndWaitForData(7878, "From Remote");
    }

    @Test
    public void shouldAllowSettingUpARouteAndConnectingToTheRemoteServerAndSendingData() {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.start();

        connectToAndSend(7878, "Hello World");

        honeyPotServer.assertSomeoneConnected();
        honeyPotServer.assertDataReceived("Hello World");
    }

    @Test
    public void shouldAllowAddingMultipleRoutes() {
        HoneyPotServer otherHoneyPot = startNewHoneyPot(9999);

        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.listenOn(7979).andConnectTo("localhost", 9999);
        moxyServer.start();

        connectToAndSend(7878, "Hello World");
        connectToAndSend(7979, "Good bye");

        honeyPotServer.assertDataReceived("Hello World");
        honeyPotServer.assertDataNotReceived("Good bye");
        otherHoneyPot.assertDataReceived("Good bye");
        otherHoneyPot.assertDataNotReceived("Hello World");
    }

    @Test
    public void shouldAllowStopListeningOnAPort() {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.start();
        moxyServer.stopListeningOn(7878);

        AssertPort.assertPortIsAvailable(7878);
    }

    @Test
    public void shouldAllowAddingListenersWhileTheServerIsRunning() {
        moxyServer.start();
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);

        connectToAndSend(7878, "Hello World");

        honeyPotServer.assertDataReceived("Hello World");
    }

    @Test
    public void shouldNotBlowUpIfTheServerHasAlreadyBeenStarted() {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.start();
        moxyServer.start();

        connectToAndSend(7878, "Hello World");

        honeyPotServer.assertDataReceived("Hello World");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldBlowUpIfYouAttemptToListenOnTheSamePortMultipleTimes() {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.listenOn(7878).andConnectTo("localhost", 8080);
    }

    @Test
    public void shouldBeAbleToRestartTheServerAndYourRoutesShouldBeAvailable() {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.start();

        moxyServer.stop();
        moxyServer.start();

        connectToAndSend(7878, "Hello World");
        honeyPotServer.assertDataReceived("Hello World");
    }

    @Test
    public void shouldBlowUpIfAPortIsAlreadyInUse() {
        moxyServer.listenOn(HONEY_POT_PORT).andConnectTo("localhost", 8080);
        try {
            moxyServer.start();
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @Test
    public void shouldCleanUpAnySuccessfulBindingsIfALaterBindingFails() {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.listenOn(7979).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.listenOn(HONEY_POT_PORT).andConnectTo("localhost", HONEY_POT_PORT);

        try {
            moxyServer.start();
            fail();
        } catch (IllegalStateException e) {

        }

        AssertPort.assertPortIsAvailable(7878);
        AssertPort.assertPortIsAvailable(7979);
    }

    @Test
    public void shouldBlowUpIfAPortIsAlreadyInUseWhileTheServerIsAlreadyRunning() {
        moxyServer.listenOn(7878).andConnectTo("localhost", HONEY_POT_PORT);
        moxyServer.start();

        try {
            moxyServer.listenOn(HONEY_POT_PORT).andConnectTo("localhost", HONEY_POT_PORT);
            fail();
        } catch (IllegalStateException e) {

        }

        connectToAndSend(7878, "Hello World");
        honeyPotServer.assertDataReceived("Hello World");
    }

    private void connectToMoxyAndWaitForData(int portToConnectTo, String expectedData) {
        try (Socket socket = new Socket()) {
            socket.setReuseAddress(true);
            socket.setSoTimeout(1000);
            socket.connect(new InetSocketAddress("localhost", portToConnectTo));

            final ArrayList<String> dataHolder = new ArrayList<>(1);
            CountDownLatch waitForDataLatch = new CountDownLatch(1);
            new ConsumeDataThread(socket, new ConsumeDataThread.Listener() {
                public void newDataReceived(Socket socket, byte[] data) {
                    dataHolder.add(new String(data));
                    waitForDataLatch.countDown();
                }
            }).start();

            try {
                waitForDataLatch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail();
            }

            assertFalse("We never received any data", dataHolder.isEmpty());
            Assert.assertEquals(expectedData, dataHolder.get(0));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void connectToAndSend(int portToConnectTo, String dataToSend) {
        SocketUtil.connectToAndSend("localhost", portToConnectTo, dataToSend);
    }

    private HoneyPotServer startNewHoneyPot(int port) {
        HoneyPotServer honeyPotServer = new HoneyPotServer(port);
        honeyPotServer.setLog(log);
        honeyPotServer.start();
        honeyPots.add(honeyPotServer);
        return honeyPotServer;
    }
}