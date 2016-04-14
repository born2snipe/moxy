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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class MoxyServerTest {
    private MoxyServer moxyServer;
    private HoneyPotServer honeyPotServer;

    @Before
    public void setUp() throws Exception {
        honeyPotServer = new HoneyPotServer(9090);
        honeyPotServer.start();

        moxyServer = new MoxyServer();
    }

    @After
    public void tearDown() throws Exception {
        honeyPotServer.stop();
        moxyServer.stop();
    }

    @Test
    public void shouldAllowSettingUpARouteAndConnectingToTheRemoteServer() {
        moxyServer.listenOn(7878).andConnectTo("localhost", 9090);
        moxyServer.start();

        connectToMoxyAndSend(7878, "Hello World");

        honeyPotServer.assertSomeoneConnected();
        honeyPotServer.assertDataReceived("Hello World");
    }

    private void connectToMoxyAndSend(int portToConnectTo, String dataToSend) {
        try (Socket socket = new Socket()) {
            socket.setReuseAddress(true);
            socket.setSoTimeout(1000);
            socket.connect(new InetSocketAddress("localhost", portToConnectTo));
            OutputStream output = socket.getOutputStream();
            output.write(dataToSend.getBytes());
            output.flush();
            output.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}