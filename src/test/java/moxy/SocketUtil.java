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

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SocketUtil {
    public static void connectToAndSend(String ip, int portToConnectTo, String dataToSend) {
        try (Socket socket = new Socket()) {
            socket.setReuseAddress(true);
            socket.setSoTimeout(1000);
            socket.connect(new InetSocketAddress(ip, portToConnectTo));
            OutputStream output = socket.getOutputStream();
            output.write(dataToSend.getBytes());
            output.flush();
            output.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void ensurePortIsInUse(int port) {
        try {
            ensurePortIsAvailable(port);
            fail();
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof BindException);
        }
    }

    public static void ensurePortIsAvailable(int port) {
        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(port));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
