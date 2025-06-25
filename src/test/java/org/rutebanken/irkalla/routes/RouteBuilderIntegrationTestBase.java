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

package org.rutebanken.irkalla.routes;

import org.apache.camel.CamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.entur.pubsub.base.EnturGooglePubSubAdmin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

@CamelSpringBootTest
@UseAdviceWith
@ActiveProfiles({"default",  "in-memory-blobstore", "test","google-pubsub-autocreate", "no-kafka"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class RouteBuilderIntegrationTestBase {

    private static PubSubEmulatorContainer pubsubEmulator;

    @Autowired
    private EnturGooglePubSubAdmin enturGooglePubSubAdmin;

    @Autowired
    protected CamelContext context;

    @BeforeAll
    public static void init() {
        pubsubEmulator =
                new PubSubEmulatorContainer(
                        DockerImageName.parse(
                                "gcr.io/google.com/cloudsdktool/cloud-sdk:emulators"
                        )
                );
        pubsubEmulator.start();
    }

    @AfterAll
    public static void tearDown() {
        pubsubEmulator.stop();
    }


    @AfterEach
    void stopContext() {
        context.stop();
    }

    @AfterEach
    public void teardown() {
        enturGooglePubSubAdmin.deleteAllSubscriptions();
    }

    @DynamicPropertySource
    static void emulatorProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.cloud.gcp.pubsub.emulator-host",
                pubsubEmulator::getEmulatorEndpoint
        );
        registry.add(
                "camel.component.google-pubsub.endpoint",
                pubsubEmulator::getEmulatorEndpoint
        );
    }

}
