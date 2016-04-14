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
import java.io.InputStream;
import java.net.Socket;

public class ConsumeDataThread extends Thread {
    private final Socket socket;
    private final Listener listener;

    public ConsumeDataThread(Socket socket, Listener listener) {
        this.socket = socket;
        this.listener = listener;
    }

    public void run() {
        byte[] buffer = new byte[1024 * 10];
        int length = -1;

        try (InputStream input = this.socket.getInputStream()) {
            while (isStillConnected() && (length = input.read(buffer)) != -1) {
                byte[] data = new byte[length];
                System.arraycopy(buffer, 0, data, 0, length);
                listener.newDataReceived(this.socket, data);
            }
        } catch (IOException e) {

        }
    }

    private boolean isStillConnected() {
        return socket.isConnected();
    }

    public interface Listener {
        void newDataReceived(Socket socket, byte[] data);
    }
}
