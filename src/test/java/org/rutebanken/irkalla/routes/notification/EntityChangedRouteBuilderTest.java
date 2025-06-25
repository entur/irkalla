package org.rutebanken.irkalla.routes.notification;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;
import org.rutebanken.irkalla.domain.CrudAction;
import org.rutebanken.irkalla.domain.EntityChangedEvent;
import org.rutebanken.irkalla.routes.RouteBuilderIntegrationTestBase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

@SpringBootTest
@CamelSpringBootTest
@ActiveProfiles(value = {"default", "in-memory-blobstore", "test", "google-pubsub-autocreate"}, inheritProfiles = false)
public class EntityChangedRouteBuilderTest extends RouteBuilderIntegrationTestBase {

    @Produce("google-pubsub:{{irkalla.pubsub.project.id}}:ror.tiamat.changelog")
    protected ProducerTemplate tiamatChangelog;


    @EndpointInject("mock:handleStopPlaceChanges")
    protected MockEndpoint handleStopPlaceChanges;

    @EndpointInject("mock:sendToKafka")
    protected MockEndpoint sendToKafka;


    @Test
    public void handleStopPlaceChanges() throws Exception {

        String stopPlaceId = "NSR:StopPlace:34";
        EntityChangedEvent entityChangedEvent = createEntityChangeEvent(stopPlaceId);


        AdviceWith.adviceWith(context, "entity-changed-route",
                a -> a.interceptSendToEndpoint( "direct:handleStopPlaceChanged")
                        .skipSendToOriginalEndpoint().to("mock:handleStopPlaceChanges"));


        AdviceWith.adviceWith(context, "notify-consumers",
                a -> a.weaveById("to-kafka-topic-event").replace().to("mock:sendToKafka"));


        context.start();

        handleStopPlaceChanges.expectedMessageCount(1);
        tiamatChangelog.sendBody(entityChangedEvent.toString());

        handleStopPlaceChanges.assertIsSatisfied();


        sendToKafka.expectedMessageCount(1);



    }

    private EntityChangedEvent createEntityChangeEvent(String stopPlaceId) {
        EntityChangedEvent entityChangedEvent = new EntityChangedEvent();
        entityChangedEvent.setEntityType(EntityChangedEvent.EntityType.STOP_PLACE);
        entityChangedEvent.setCrudAction(CrudAction.UPDATE);
        entityChangedEvent.setEntityId(stopPlaceId);
        entityChangedEvent.setEntityVersion(Instant.now().toEpochMilli());
        entityChangedEvent.setEntityChanged(Instant.now().toEpochMilli());
        entityChangedEvent.setMsgId("dummy-id");

        return entityChangedEvent;
    }
}
