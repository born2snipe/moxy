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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProxyHttpAppTest {

    private MoxyServer moxy;
    private HttpApp http;
    private AssertableListener assertableListener;

    @Before
    public void setUp() throws Exception {
        String path = Thread.currentThread().getContextClassLoader().getResource("config.yml").getPath();
        http = new HttpApp();
        http.run("server", path);

        assertableListener = new AssertableListener();

        moxy = new MoxyServer();
        moxy.listenOn(6565).andConnectTo("localhost", 8081);
        moxy.addListener(assertableListener);
        moxy.start();
    }

    @After
    public void tearDown() throws Exception {
        moxy.stop();
    }

    @Test
    public void canProxyGetRequests() throws Exception {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet request = new HttpGet("http://localhost:6565/healthcheck");

        CloseableHttpResponse response = httpclient.execute(request);

        try {
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("{\"deadlocks\":{\"healthy\":true}}", EntityUtils.toString(response.getEntity()));
            assertableListener.assertConnectionWasMadeOn(6565);
        } finally {
            httpclient.close();
        }
    }


}
