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

import moxy.MoxyListener;

import java.net.Socket;
import java.util.ArrayList;

public class RelayInfo {
    private Socket listener;
    private Socket routeTo;
    private ArrayList<Thread> threads = new ArrayList<>();

    public RelayInfo(Socket listener, Socket routeTo) {
        this.listener = listener;
        this.routeTo = routeTo;
    }

    public void startRelaying(final MoxyListener dispatchListener) {
        ReadAndSendDataThread listenerToRouteTo = new ReadAndSendDataThread(listener, routeTo) {
            @Override
            protected void sentData(byte[] data) {
                dispatchListener.sentData(listener.getLocalPort(), routeTo.getRemoteSocketAddress(), data);
            }
        };
        listenerToRouteTo.start();

        ReadAndSendDataThread routeToToListener = new ReadAndSendDataThread(routeTo, listener) {
            @Override
            protected void sentData(byte[] data) {
                dispatchListener.receivedData(listener.getLocalPort(), routeTo.getRemoteSocketAddress(), data);
            }
        };
        routeToToListener.start();

        threads.add(listenerToRouteTo);
        threads.add(routeToToListener);
    }

    public void stopRelaying() {
        for (Thread thread : threads) {
            ThreadKiller.killAndWait(thread);
        }
    }
}