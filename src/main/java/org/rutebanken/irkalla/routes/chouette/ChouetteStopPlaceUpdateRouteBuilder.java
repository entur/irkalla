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

package org.rutebanken.irkalla.routes.chouette;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.http.common.HttpMethods;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.rutebanken.irkalla.Constants;
import org.rutebanken.irkalla.routes.BaseRouteBuilder;
import org.rutebanken.irkalla.util.BatchNumber;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

import static org.apache.camel.support.builder.PredicateBuilder.or;
import static org.rutebanken.irkalla.Constants.BATCH_NUMBER;
import static org.rutebanken.irkalla.Constants.CAMEL_BREADCRUMB_ID;
import static org.rutebanken.irkalla.Constants.HEADER_NEXT_BATCH_URL;
import static org.rutebanken.irkalla.Constants.HEADER_SYNC_OPERATION;
import static org.rutebanken.irkalla.Constants.HEADER_SYNC_STATUS_TO;
import static org.rutebanken.irkalla.Constants.SYNC_OPERATION_DELTA;
import static org.rutebanken.irkalla.Constants.SYNC_OPERATION_FULL;
import static org.rutebanken.irkalla.Constants.SYNC_OPERATION_FULL_WITH_DELETE_UNUSED_FIRST;


@Component
public class ChouetteStopPlaceUpdateRouteBuilder extends BaseRouteBuilder {

    // setting socket read timeout to 5 minutes
    private static final String HTTP_TIMEOUT_CONFIG = "?httpConnection.soTimeout=300000";

    @Value("${chouette.url}")
    private String chouetteUrl;


    @Value("${chouette.sync.stop.place.cron:0 0/5 * * * ?}")
    private String deltaSyncCronSchedule;

    @Value("${chouette.sync.stop.place.full.cron:0 0 2 * * ?}")
    private String fullSyncCronSchedule;


    @Value("${chouette.sync.stop.place.retry.delay:15000}")
    private int retryDelay;

    @Value("${chouette.sync.stop.place.grace.ms:60000}")
    private int graceMilliseconds;


    @Override
    public void configure() throws Exception {
        super.configure();

        from("quartz://irkalla/stopPlaceDeltaSync?cron=" + deltaSyncCronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{chouette.sync.stop.place.autoStartup:true}}")
                .log(LoggingLevel.DEBUG, "Quartz triggers delta sync of changed stop places.")
                .setHeader(HEADER_SYNC_OPERATION, constant(SYNC_OPERATION_DELTA))
                .to(ExchangePattern.InOnly,"google-pubsub:{{irkalla.pubsub.project.id}}:ChouetteStopPlaceSyncQueue")
                .routeId("chouette-synchronize-stop-places-delta-quartz");

        from("quartz://irkalla/stopPlaceSync?cron=" + fullSyncCronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{chouette.sync.stop.place.autoStartup:true}}")
                .log(LoggingLevel.DEBUG, "Quartz triggers full sync of changed stop places.")
                .setHeader(HEADER_SYNC_OPERATION, constant(SYNC_OPERATION_FULL_WITH_DELETE_UNUSED_FIRST))
                .to(ExchangePattern.InOnly,"google-pubsub:{{irkalla.pubsub.project.id}}:ChouetteStopPlaceSyncQueue")
                .routeId("chouette-synchronize-stop-places-full-quartz");


        // acknowledgment mode switched to NONE so that the ack/nack callback can be set after message aggregation.
        from("master:lockOnChouetteStopPlaceSyncQueue:google-pubsub:{{irkalla.pubsub.project.id}}:ChouetteStopPlaceSyncQueue")
                .process(this::removeSynchronizationForAggregatedExchange)
                .aggregate(constant(true)).aggregationStrategy(new GroupedMessageAggregationStrategy()).completionSize(100).completionTimeout(1000)
                .process(this::addSynchronizationForAggregatedExchange)
                .process(this::mergePubSubMessages)
                .choice()
                .when(simple("${header." + HEADER_SYNC_OPERATION + "} == '" + SYNC_OPERATION_FULL_WITH_DELETE_UNUSED_FIRST + "'"))
                .to("direct:deleteUnusedStopPlaces")
                .otherwise()
                .to("direct:synchronizeStopPlaces")
                .end()
                .routeId("chouette-synchronize-stop-places-control-route");


        from("direct:synchronizeStopPlaces")
                .process( e -> {
                    MDC.put(CAMEL_BREADCRUMB_ID, e.getIn().getHeader(Exchange.BREADCRUMB_ID, String.class));
                    MDC.put(HEADER_SYNC_OPERATION, e.getIn().getHeader(HEADER_SYNC_OPERATION, String.class));
                })
                .choice()
                .when(or(header(HEADER_NEXT_BATCH_URL).isNull(), header(HEADER_NEXT_BATCH_URL).isEqualTo(""))) // New sync, init
                 .to("direct:initNewSynchronization")
                .otherwise()
                 .process(e -> MDC.put(BATCH_NUMBER, BatchNumber.getTiamatUrlPageNumber(e.getIn().getHeader(HEADER_NEXT_BATCH_URL, String.class))))
                 .log(LoggingLevel.INFO, "Next batch header is : ${header." + HEADER_NEXT_BATCH_URL + "}")
                 .log(LoggingLevel.INFO, "${header." + HEADER_SYNC_OPERATION + "} synchronization of stop places in Chouette resumed.")
                .end()
                //.setBody(constant(""))
                .to("direct:processChangedStopPlacesAsNetex")
                .choice()
                .when(header(HEADER_NEXT_BATCH_URL).isNotNull())
                .setBody(constant(""))
                .to("google-pubsub:{{irkalla.pubsub.project.id}}:ChouetteStopPlaceSyncQueue")  // Prepare new iteration
                .otherwise()
                .to("direct:completeSynchronization") // Completed

                .end()

                .routeId("chouette-synchronize-stop-places");


        from("direct:initNewSynchronization")
                .process(e -> MDC.put(HEADER_SYNC_OPERATION, e.getIn().getHeader(HEADER_SYNC_OPERATION, String.class)))
                .log(LoggingLevel.INFO, "${header." + HEADER_SYNC_OPERATION + "} synchronization of stop places in Chouette started.")
                .choice()
                .when(simple("${header." + HEADER_SYNC_OPERATION + "} == '" + SYNC_OPERATION_DELTA + "'"))
                //.setBody(constant(""))
                .to("direct:getSyncStatusUntilTime")
                .setHeader(Constants.HEADER_SYNC_STATUS_FROM, simple("${body}"))
                .end()

                .process(e -> e.getIn().setHeader(Constants.HEADER_SYNC_STATUS_TO, Instant.now().toEpochMilli()))
                .routeId("chouette-synchronize-stop-places-init");

        from("direct:completeSynchronization")
                .choice()
                .when(header(Constants.HEADER_SYNC_STATUS_TO).isNotNull())
                // Adjust sync status back in time to be sure to catch any historic changes not yet committed in stop place registry
                .process(e -> e.getIn().setBody(Instant.ofEpochMilli(e.getIn().getHeader(HEADER_SYNC_STATUS_TO, Long.class)).minusMillis(graceMilliseconds)))
                .to("direct:setSyncStatusUntilTime")
                .log(LoggingLevel.INFO, "${header." + HEADER_SYNC_OPERATION + "} synchronization of stop places in Chouette completed.")
                .otherwise()
                .log(LoggingLevel.INFO, "${header." + HEADER_SYNC_OPERATION + "} synchronization of stop places in Chouette completed, unable to update etcd.")
                .end()

                .routeId("chouette-synchronize-stop-places-complete");


        from("direct:deleteUnusedStopPlaces")
                .process(e -> MDC.put(HEADER_SYNC_OPERATION, e.getIn().getHeader(HEADER_SYNC_OPERATION, String.class)))
                .log(LoggingLevel.INFO, "Full synchronization of stop places in Chouette, deleting unused stops first")
                .removeHeaders("CamelHttp*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.DELETE))
                .doTry()
                .log(LoggingLevel.INFO, "Sending unused stop places deletion request to Chouette")
                .toD(chouetteUrl + "/chouette_iev/stop_place/unused" + HTTP_TIMEOUT_CONFIG)
                .log(LoggingLevel.INFO, "Sent unused stop places deletion request to Chouette")
                .setHeader(HEADER_SYNC_OPERATION, constant(SYNC_OPERATION_FULL))
                .log(LoggingLevel.INFO, "Deleting unused stop places in Chouette completed.")
                .setBody(constant(""))
                .to("google-pubsub:{{irkalla.pubsub.project.id}}:ChouetteStopPlaceSyncQueue")
                .doCatch(HttpOperationFailedException.class).onWhen(exchange -> {
            HttpOperationFailedException ex = exchange.getException(HttpOperationFailedException.class);
            return (ex.getStatusCode() == 423);
        })
                .log(LoggingLevel.INFO, "Unable to delete unused stop places because Chouette is busy, retry in " + retryDelay + " ms")
                .delay(retryDelay)
                .setBody(constant(""))
                .to("google-pubsub:{{irkalla.pubsub.project.id}}:ChouetteStopPlaceSyncQueue")
                .stop()
                .routeId("chouette-synchronize-stop-places-delete-unused");


        from("direct:synchronizeStopPlaceBatch")
                .convertBodyTo(String.class)
                .removeHeaders("CamelHttp*")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .doTry()
                .toD(chouetteUrl + "/chouette_iev/stop_place" + HTTP_TIMEOUT_CONFIG)
                .log(LoggingLevel.INFO, "Sent stop places update request to Chouette")
                .log(LoggingLevel.DEBUG, "Response from Chouette: ${body}")
                .log(LoggingLevel.DEBUG, "Response Headers from Chouette: ${headers}")
                .doCatch(HttpOperationFailedException.class).onWhen(exchange -> {
                            HttpOperationFailedException ex = exchange.getException(HttpOperationFailedException.class);
                            return (ex.getStatusCode() == 423);})
                .log(LoggingLevel.INFO, "Unable to sync stop places because Chouette is busy, retry in " + retryDelay + " ms")
                .delay(retryDelay)
                .setBody(constant(""))
                .to("google-pubsub:{{irkalla.pubsub.project.id}}:ChouetteStopPlaceSyncQueue")
                .stop()
                .routeId("chouette-synchronize-stop-place-batch");

    }

    /**
     * Merge status from all msg read in batch into current exchange.
     * <p>
     * Priority:
     * - Delete and start new, full sync if at least one message signals that
     * - Full sync, if no delete and at least one message signals that, using first msg with url set (indicating ongoing job) if any
     * - Delta sync in other cases, using first msg with url set (indicating ongoing job) if any
     */
    private void mergePubSubMessages(Exchange e) {
        List<Message> msgList = e.getIn().getBody(List.class);
        msgList.sort(new SyncMsgComparator());

        Message topPriMsg = msgList.getFirst();
        Object syncOperation = topPriMsg.getHeader(HEADER_SYNC_OPERATION);
        if (syncOperation == null) {
            e.getIn().setHeader(HEADER_SYNC_OPERATION, SYNC_OPERATION_DELTA);
        } else {
            e.getIn().setHeader(HEADER_SYNC_OPERATION, syncOperation);
            e.getIn().setHeader(HEADER_SYNC_STATUS_TO, topPriMsg.getHeader(HEADER_SYNC_STATUS_TO));
            e.getIn().setHeader(HEADER_NEXT_BATCH_URL, topPriMsg.getHeader(HEADER_NEXT_BATCH_URL));
        }
    }

}
