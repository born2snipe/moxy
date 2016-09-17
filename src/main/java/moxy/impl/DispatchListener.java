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

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DispatchListener extends MoxyListener {
    private List<MoxyListener> delegates = Collections.synchronizedList(new ArrayList<>());

    public void addListener(MoxyListener listener) {
        delegates.add(listener);
    }

    @Override
    public void connectionMade(int listenerPort, SocketAddress remoteAddress) {
        for (MoxyListener delegate : delegates) {
            delegate.connectionMade(listenerPort, remoteAddress);
        }
    }

    @Override
    public void sentData(int listenPort, SocketAddress remoteAddress, byte[] data) {
        for (MoxyListener delegate : delegates) {
            delegate.sentData(listenPort, remoteAddress, data);
        }
    }

    public void receivedData(int listenPort, SocketAddress remoteAddress, byte[] data) {
        for (MoxyListener delegate : delegates) {
            delegate.receivedData(listenPort, remoteAddress, data);
        }
    }
}
