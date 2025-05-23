/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package org.rutebanken.irkalla;

import org.apache.camel.builder.RouteBuilder;
import org.entur.pubsub.base.config.GooglePubSubConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * A spring-boot application that includes a Camel route builder to set up the Camel context.
 */
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableScheduling
@Import({GooglePubSubConfig.class})
public class IrkallaApplication extends RouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(IrkallaApplication.class);

    @Value("${irkalla.shutdown.timeout:45}")
    private Long shutdownTimeout;

    // must have a main method spring-boot can run
    public static void main(String[] args) {
        LOGGER.info("Starting Irkalla...");
        SpringApplication.run(IrkallaApplication.class, args);
    }

    @Override
    public void configure() {
        getContext().getShutdownStrategy().setTimeout(shutdownTimeout);
        getContext().setUseMDCLogging(true);
    }
}
