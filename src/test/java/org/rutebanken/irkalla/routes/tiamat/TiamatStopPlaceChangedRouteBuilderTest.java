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

package org.rutebanken.irkalla.routes.tiamat;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rutebanken.irkalla.Constants;
import org.rutebanken.irkalla.IrkallaApplication;
import org.rutebanken.irkalla.domain.CrudAction;
import org.rutebanken.irkalla.routes.RouteBuilderIntegrationTestBase;
import org.rutebanken.irkalla.routes.tiamat.graphql.model.Name;
import org.rutebanken.irkalla.routes.tiamat.graphql.model.StopPlace;
import org.rutebanken.irkalla.routes.tiamat.graphql.model.ValidBetween;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.Map;

@CamelSpringBootTest
@SpringBootTest(classes = IrkallaApplication.class)
class TiamatStopPlaceChangedRouteBuilderTest extends RouteBuilderIntegrationTestBase {

    @MockBean(name = "stopPlaceDao")
    private StopPlaceDao stopPlaceDao;

    @Produce("direct:handleStopPlaceChanged")
    private ProducerTemplate handleStopPlaceChanged;

    @EndpointInject("mock:chouetteDeleteQueue")
    private MockEndpoint chouetteDeleteQueue;

    @EndpointInject("mock:crudEventQueue")
    private MockEndpoint crudEventQueue;

    @EndpointInject("mock:chouetteSyncQueue")
    private MockEndpoint chouetteSyncQueue;

    @Test
    void deleteDispatchesToChouetteDeleteQueue() throws Exception {
        AdviceWith.adviceWith(context, "tiamat-stop-place-changed", a ->
                a.weaveByToUri("google-pubsub:(.*):ChouetteStopPlaceDeleteQueue")
                        .replace().to("mock:chouetteDeleteQueue"));

        context.start();
        chouetteDeleteQueue.expectedMessageCount(1);

        handleStopPlaceChanged.sendBodyAndHeaders("", Map.of(
                Constants.HEADER_CRUD_ACTION, CrudAction.DELETE,
                Constants.HEADER_ENTITY_ID, "NSR:StopPlace:1",
                Constants.HEADER_ENTITY_VERSION, 1L));

        chouetteDeleteQueue.assertIsSatisfied();
    }

    @Test
    void unknownStopIsDiscarded() throws Exception {
        Mockito.when(stopPlaceDao.getStopPlaceChange(
                Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(null);

        AdviceWith.adviceWith(context, "tiamat-stop-place-changed", a ->
                a.weaveByToUri("google-pubsub:(.*):CrudEventQueue").replace().to("mock:crudEventQueue"));
        AdviceWith.adviceWith(context, "tiamat-trigger-chouette-update-for-changed-stop", a ->
                a.weaveByToUri("google-pubsub:(.*):ChouetteStopPlaceSyncQueue").replace().to("mock:chouetteSyncQueue"));

        context.start();
        crudEventQueue.expectedMessageCount(0);
        chouetteSyncQueue.expectedMessageCount(0);

        handleStopPlaceChanged.sendBodyAndHeaders("", Map.of(
                Constants.HEADER_CRUD_ACTION, CrudAction.UPDATE,
                Constants.HEADER_ENTITY_ID, "NSR:StopPlace:999",
                Constants.HEADER_ENTITY_VERSION, 1L));

        crudEventQueue.assertIsSatisfied();
        chouetteSyncQueue.assertIsSatisfied();
    }

    @Test
    void effectiveChangeSendsToCrudEventQueueAndTriggersChouetteSync() throws Exception {
        StopPlaceChange change = stopPlaceChange(CrudAction.UPDATE, Instant.now().minusSeconds(60));
        Mockito.when(stopPlaceDao.getStopPlaceChange(
                Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(change);

        AdviceWith.adviceWith(context, "tiamat-stop-place-changed", a ->
                a.weaveByToUri("google-pubsub:(.*):CrudEventQueue").replace().to("mock:crudEventQueue"));
        AdviceWith.adviceWith(context, "tiamat-trigger-chouette-update-for-changed-stop", a ->
                a.weaveByToUri("google-pubsub:(.*):ChouetteStopPlaceSyncQueue").replace().to("mock:chouetteSyncQueue"));

        context.start();
        crudEventQueue.expectedMessageCount(1);
        chouetteSyncQueue.expectedMessageCount(1);

        handleStopPlaceChanged.sendBodyAndHeaders("", Map.of(
                Constants.HEADER_CRUD_ACTION, CrudAction.UPDATE,
                Constants.HEADER_ENTITY_ID, "NSR:StopPlace:1",
                Constants.HEADER_ENTITY_VERSION, 2L));

        crudEventQueue.assertIsSatisfied();
        chouetteSyncQueue.assertIsSatisfied();
    }

    @Test
    void futureChangeSendsToCrudEventQueueButDoesNotTriggerChouetteSync() throws Exception {
        StopPlaceChange change = stopPlaceChange(CrudAction.UPDATE, Instant.now().plusSeconds(3600));
        Mockito.when(stopPlaceDao.getStopPlaceChange(
                Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(change);

        AdviceWith.adviceWith(context, "tiamat-stop-place-changed", a ->
                a.weaveByToUri("google-pubsub:(.*):CrudEventQueue").replace().to("mock:crudEventQueue"));
        AdviceWith.adviceWith(context, "tiamat-trigger-chouette-update-for-changed-stop", a ->
                a.weaveByToUri("google-pubsub:(.*):ChouetteStopPlaceSyncQueue").replace().to("mock:chouetteSyncQueue"));

        context.start();
        crudEventQueue.expectedMessageCount(1);
        chouetteSyncQueue.expectedMessageCount(0);

        handleStopPlaceChanged.sendBodyAndHeaders("", Map.of(
                Constants.HEADER_CRUD_ACTION, CrudAction.UPDATE,
                Constants.HEADER_ENTITY_ID, "NSR:StopPlace:1",
                Constants.HEADER_ENTITY_VERSION, 2L));

        crudEventQueue.assertIsSatisfied();
        chouetteSyncQueue.assertIsSatisfied();
    }

    private StopPlaceChange stopPlaceChange(CrudAction action, Instant changeTime) {
        StopPlace current = new StopPlace();
        current.id = "NSR:StopPlace:1";
        current.version = 2L;
        current.name = new Name("Test Stop");
        current.validBetween = new ValidBetween();
        current.validBetween.fromDate = changeTime;
        return new StopPlaceChange(action, current, null);
    }
}