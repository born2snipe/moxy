/**
 * Copyright to the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package moxy;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

public class HttpApp extends Application<HttpApp.Config> {
    @Override
    public void initialize(Bootstrap bootstrap) {

    }

    @Override
    public void run(Config configuration, Environment environment) throws Exception {
        environment.jersey().register(new EndPoint());
    }

    public static class EndPoint {
        @GET
        @Path("/test")
        public Response getCatalog() {
            System.out.println("EndPoint.getCatalog");
            return Response.ok("test-data").build();
        }

    }

    public static class Config extends Configuration {

    }
}