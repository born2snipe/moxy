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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

public class ReadAndSendDataThread extends Thread {
    private static final Log LOG = Log.get(ReadAndSendDataThread.class);
    private final Socket input;
    private final Socket output;

    public ReadAndSendDataThread(Socket input, Socket output) {
        this.input = input;
        this.output = output;
        setDaemon(true);
        setName("READ FROM: " + input + ", SEND TO: " + output);
    }

    public void run() {
        byte[] buffer = new byte[1024 * 10];
        int length = -1;

        try (InputStream input = this.input.getInputStream(); OutputStream output = this.output.getOutputStream()) {
            while (isStillConnected() && (length = input.read(buffer)) != -1) {
                LOG.info(getName() + " -- " + length + " bytes of data");

                byte[] dataToSend = new byte[length];
                System.arraycopy(buffer, 0, dataToSend, 0, length);

                output.write(dataToSend);
                sentData(dataToSend);

                output.flush();
                if (LOG.isDebug()) {
                    LOG.debug(getName() + " -- DATA=[" + new String(dataToSend) + "]");
                }
                pause();
            }
        } catch (SocketException e) {
            if (this.input.isClosed()) {
                LOG.debug("READ FROM: Connection was closed: " + input);
            } else if (this.output.isClosed()) {
                LOG.debug("SEND TO: Connection was closed: " + output);
            } else {
                LOG.error("An error occurred on thread: " + getName(), e);
            }
        } catch (IOException e) {
            LOG.error("An error occurred on thread: " + getName(), e);
        } finally {
            LOG.debug("Thread died: " + getName());
            closeConnections();
        }
    }

    protected void sentData(byte[] data) {

    }

    @Override
    public void interrupt() {
        closeConnections();
        super.interrupt();
    }

    private void closeConnections() {
        close(input);
        close(output);
    }

    private void close(Socket socket) {
        if (socket.isConnected()) {
            try {
                socket.close();
                LOG.info("Closed socket: " + socket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void pause() {
        try {
            Thread.sleep(10L);
        } catch (InterruptedException e) {

        }
    }

    private boolean isStillConnected() {
        return this.input.isConnected() && this.output.isConnected();
    }
}
