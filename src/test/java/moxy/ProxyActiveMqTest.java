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

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jms.core.JmsMessagingTemplate;

import static org.junit.Assert.assertEquals;

public class ProxyActiveMqTest {
    private BrokerService broker;
    private JmsMessagingTemplate jms;
    private MoxyServer moxy;

    @Before
    public void setUp() throws Exception {
        broker = new BrokerService();
        broker.addConnector("tcp://localhost:61616");
        broker.start();

        moxy = new MoxyServer();
        moxy.setLog(new SysOutLog(Log.Level.DEBUG));
        moxy.listenOn(7878).andConnectTo("localhost", 61616);
        moxy.start();

        jms = newJmsTemplate(7878);
    }

    @After
    public void tearDown() throws Exception {
        broker.stop();
        moxy.stop();
    }

    @Test
    public void shouldBeAbleToSimulateThereAreMultipleBrokers() {
        moxy.listenOn(7979).andConnectTo("localhost", 61616);

        JmsMessagingTemplate otherJms = newJmsTemplate(7979);

        assertWeCanSendAndReceiveAMessageOn(jms);
        assertWeCanSendAndReceiveAMessageOn(otherJms);
    }

    @Test
    public void shouldBeAbleToProxyActiveMq() {
        assertWeCanSendAndReceiveAMessageOn(jms);
    }

    private void assertWeCanSendAndReceiveAMessageOn(JmsMessagingTemplate jms) {
        final String queueName = "testQueue-" + System.nanoTime();
        final String message = String.valueOf(System.nanoTime());

        jms.convertAndSend(queueName, message);


        RetryableAssertion retryable = new RetryableAssertion() {
            protected void assertion() {
                assertEquals(message, jms.receiveAndConvert(queueName, String.class));

            }
        };
        retryable.performAssertion();
    }

    private JmsMessagingTemplate newJmsTemplate(int port) {
        return new JmsMessagingTemplate(new ActiveMQConnectionFactory("tcp://localhost:" + port));
    }
}
