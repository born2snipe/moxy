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

public class ReadAndSendDataThread extends Thread {
    private final Socket input;
    private final Socket output;
    private final Log log;

    public ReadAndSendDataThread(Socket input, Socket output, Log log) {
        this.input = input;
        this.output = output;
        this.log = log;
        setDaemon(true);
        setName("READ FROM: " + input + ", SEND TO: " + output);
    }

    public void run() {
        byte[] buffer = new byte[1024 * 10];
        int length = -1;

        try (InputStream input = this.input.getInputStream(); OutputStream output = this.output.getOutputStream()) {
            while (isStillConnected() && (length = input.read(buffer)) != -1) {
                log.debug(getName() + " -- " + length + " bytes of data");
                output.write(buffer, 0, length);
                output.flush();
                pause();
            }
        } catch (IOException e) {
            log.error("An error occurred on thread: " + getName(), e);
        } finally {
            log.debug("Thread dieing: " + getName());
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