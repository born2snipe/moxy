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

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;

public class ConnectTo {
    public final InetSocketAddress socketAddress;
    private final Log log;
    private ArrayList<Thread> threads = new ArrayList<>();
    // todo - need to find a way to get these to auto cleanup on death
    private ArrayList<RelayInfo> relayInfos = new ArrayList<>();

    public ConnectTo(InetSocketAddress socketAddress, Log log) {
        this.socketAddress = socketAddress;
        this.log = log;
    }

    public Thread associateThread(Thread thread) {
        threads.add(thread);
        return thread;
    }

    public void shutdown() {
        if (threads.size() > 0) {
            threads.forEach(ThreadKiller::killAndWait);
            threads.clear();
        }

        for (RelayInfo relayInfo : relayInfos) {
            relayInfo.stopRelaying();
        }
        relayInfos.clear();
    }

    public void startReadingAndWriting(Socket listener, Socket routeTo) {
        RelayInfo relayInfo = new RelayInfo(listener, routeTo);
        relayInfo.startRelaying();
        relayInfos.add(relayInfo);
    }

    private class RelayInfo {
        private Socket listener;
        private Socket routeTo;
        private ArrayList<Thread> threads = new ArrayList<>();

        public RelayInfo(Socket listener, Socket routeTo) {
            this.listener = listener;
            this.routeTo = routeTo;
        }

        public void startRelaying() {
            ReadAndSendDataThread listenerToRouteTo = new ReadAndSendDataThread(listener, routeTo, log);
            listenerToRouteTo.start();
            ReadAndSendDataThread routeToToListener = new ReadAndSendDataThread(routeTo, listener, log);
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
}
