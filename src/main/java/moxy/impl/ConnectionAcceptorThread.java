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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConnectionAcceptorThread extends Thread {
    private final int port;
    private final Log log;
    private final Listener listener;
    private ServerSocket serverSocket;
    private AtomicBoolean kill = new AtomicBoolean(false);
    private AtomicBoolean closing = new AtomicBoolean(false);

    public ConnectionAcceptorThread(int port, Log log, Listener listener) {
        this.port = port;
        this.log = log;
        this.listener = listener;
        setDaemon(true);
        setName("AWAITING CONNECTIONS ON PORT: " + port);
    }

    public void run() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));
            listener.boundToLocalPort(port);

            while (!kill.get()) {
                Socket socket = serverSocket.accept();
                socket.setReuseAddress(true);

                log.debug(getName() + " -- New Connection made: " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
                listener.newConnection(socket);

                try {
                    pause();
                } catch (InterruptedException e) {
                    kill.set(true);
                }
            }

        } catch (IOException e) {
            if (!closing.get()) {
                log.error("A problem occurred on thread: " + getName(), e);
            }
        } finally {
            close();
        }

    }

    @Override
    public void interrupt() {
        kill.set(true);
        close();
        super.interrupt();
    }

    private void close() {
        closing.set(true);

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {

            }
        }
    }

    private void pause() throws InterruptedException {
        Thread.sleep(10);
    }

    public interface Listener {
        void newConnection(Socket socket) throws IOException;

        void boundToLocalPort(int port);
    }
}
