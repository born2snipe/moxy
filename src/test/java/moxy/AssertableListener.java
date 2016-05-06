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

import java.net.SocketAddress;
import java.util.ArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AssertableListener extends MoxyListener {
    private ArrayList<Integer> connectionsMade = new ArrayList<>();

    @Override
    public void connectionMade(int listenerPort, SocketAddress remoteAddress) {
        connectionsMade.add(listenerPort);
    }

    public void assertConnectionWasMadeOn(int port) {
        RetryableAssertion assertion = new RetryableAssertion() {
            protected void assertion() {
                assertTrue("No connection was made on the port [" + port + "]", connectionsMade.contains(port));
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
}
