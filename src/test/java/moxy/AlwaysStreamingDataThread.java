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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

public class AlwaysStreamingDataThread extends Thread {
    private final InetSocketAddress address;
    private final CountDownLatch connectedToServer = new CountDownLatch(1);

    public AlwaysStreamingDataThread(String host, int port) {
        address = new InetSocketAddress(host, port);
    }

    public void start() {
        super.start();
        try {
            connectedToServer.await();
        } catch (InterruptedException e) {

        }
    }

    public void run() {
        try (Socket socket = new Socket()) {
            socket.setReuseAddress(true);
            socket.connect(address);

            OutputStream output = socket.getOutputStream();
            while (socket.isConnected()) {
                output.write(getClass().getName().getBytes());
                output.write('\n');
                output.flush();

                pause();
                connectedToServer.countDown();
            }

        } catch (IOException e) {

        }
    }

    private void pause() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {

        }
    }
}
