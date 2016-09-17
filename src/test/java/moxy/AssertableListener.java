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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AssertableListener extends MoxyListener {
    private ArrayList<Integer> connectionsMade = new ArrayList<>();
    private ArrayList<Data> outgoingData = new ArrayList<>();
    private ArrayList<Data> incomingData = new ArrayList<>();

    @Override
    public void connectionMade(int listenerPort, SocketAddress remoteAddress) {
        connectionsMade.add(listenerPort);
    }

    @Override
    public void sentData(int listenPort, SocketAddress remoteAddress, byte[] data) {
        outgoingData.add(new Data(listenPort, remoteAddress, new String(data)));
    }

    @Override
    public void receivedData(int listenPort, SocketAddress remoteAddress, byte[] data) {
        incomingData.add(new Data(listenPort, remoteAddress, new String(data)));
    }

    public void assertConnectionWasMadeOn(int port) {
        RetryableAssertion assertion = new RetryableAssertion() {
            protected void assertion() {
                assertFalse("No connections were made ever", connectionsMade.isEmpty());
                assertTrue("No connection was made on the port [" + port + "], but connections were made on: " + connectionsMade,
                        connectionsMade.contains(port));
            }
        };
        assertion.performAssertion();
    }

    public void assertNoConnectionWasMadeOn(int port) {
        RetryableAssertion assertion = new RetryableAssertion() {
            protected void assertion() {
                assertFalse("A connection was made on the port [" + port + "]", connectionsMade.contains(port));
            }
        };
        assertion.performAssertion();
    }

    public void assertSentData(int expectedListeningPort, InetSocketAddress expectedRemoteAddress, String expectedData) {
        List<Data> data = outgoingData.stream()
                .filter((sd) -> sd.listenPort == expectedListeningPort)
                .collect(Collectors.toList());
        assertFalse("No data was sent over port: " + expectedData, data.isEmpty());

        data = data.stream()
                .filter((sd) -> expectedRemoteAddress.equals(sd.remote))
                .collect(Collectors.toList());

        List<String> dataSent = data.stream().map(Data::getData).collect(Collectors.toList());
        assertTrue("We did not send the data=[" + expectedData + "]. Actual send data=" + outgoingData, dataSent.contains(expectedData));
    }

    public void assertReceivedData(int expectedListeningPort, InetSocketAddress expectedRemoteAddress, String expectedData) {
        List<Data> data = incomingData.stream()
                .filter((sd) -> sd.listenPort == expectedListeningPort)
                .collect(Collectors.toList());
        assertFalse("No data was recieved over port: " + expectedData, data.isEmpty());

        data = data.stream()
                .filter((sd) -> expectedRemoteAddress.equals(sd.remote))
                .collect(Collectors.toList());

        List<String> dataSent = data.stream().map(Data::getData).collect(Collectors.toList());
        assertTrue("We did not receive the data=[" + expectedData + "]. Actual received data=" + outgoingData, dataSent.contains(expectedData));
    }

    public void assertNoDataSent() {
        assertTrue("Data was sent. Actual sent data=" + outgoingData, outgoingData.isEmpty());
    }

    public void assertNoDataReceived() {
        assertTrue("Data was received. Actual received data=" + incomingData, incomingData.isEmpty());
    }

    private class Data {
        int listenPort;
        SocketAddress remote;
        String data;

        public Data(int listenPort, SocketAddress remote, String data) {
            this.listenPort = listenPort;
            this.remote = remote;
            this.data = data;
        }

        public String getData() {
            return data;
        }
    }
}
