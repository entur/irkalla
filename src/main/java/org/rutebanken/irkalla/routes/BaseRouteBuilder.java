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


import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.google.pubsub.consumer.AcknowledgeAsync;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.rutebanken.irkalla.Constants;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class BaseRouteBuilder extends RouteBuilder {

    private static final String SYNCHRONIZATION_HOLDER = "SYNCHRONIZATION_HOLDER";


    @Value("${irkalla.camel.redelivery.max:3}")
    private int maxRedelivery;

    @Value("${irkalla.camel.redelivery.delay:5000}")
    private int redeliveryDelay;

    @Value("${irkalla.camel.redelivery.backoff.multiplier:3}")
    private int backOffMultiplier;


    @Override
    public void configure() throws Exception {
        errorHandler(defaultErrorHandler()
                .redeliveryDelay(redeliveryDelay)
                .maximumRedeliveries(maxRedelivery)
                .onRedelivery(this::logRedelivery)
                .useExponentialBackOff()
                .backOffMultiplier(backOffMultiplier)
                .logExhausted(true)
                .logRetryStackTrace(true));

        // Copy all PubSub headers except the internal Camel PubSub headers from the PubSub message into the Camel message headers.
        interceptFrom(".*google-pubsub:.*")
                .process(exchange ->
                {
                    Map<String, String> pubSubAttributes = exchange.getIn().getHeader(GooglePubsubConstants.ATTRIBUTES, Map.class);
                    if (pubSubAttributes == null) {
                        throw new IllegalStateException("Missing PubSub attribute maps in Exchange");
                    }
                    pubSubAttributes.entrySet()
                            .stream()
                            .filter(entry -> !entry.getKey().startsWith("CamelGooglePubsub"))
                            .forEach(entry -> exchange.getIn().setHeader(entry.getKey(), entry.getValue()));
                });

        // Copy all PubSub headers except the internal Camel PubSub headers from the Camel message into the PubSub message.
        interceptSendToEndpoint("google-pubsub:*").process(
                exchange -> {
                    Map<String, String> pubSubAttributes = new HashMap<>();
                    exchange.getIn().getHeaders().entrySet().stream()
                            .filter(entry -> !entry.getKey().startsWith("CamelGooglePubsub"))
                            .filter(entry -> Objects.toString(entry.getValue()).length() <= 1024)
                            .forEach(entry -> pubSubAttributes.put(entry.getKey(), Objects.toString(entry.getValue(), "")));
                    exchange.getIn().setHeader(GooglePubsubConstants.ATTRIBUTES, pubSubAttributes);

                });

    }

    protected void logRedelivery(Exchange exchange) {
        int redeliveryCounter = exchange.getIn().getHeader("CamelRedeliveryCounter", Integer.class);
        int redeliveryMaxCounter = exchange.getIn().getHeader("CamelRedeliveryMaxCounter", Integer.class);
        Throwable camelCaughtThrowable = exchange.getProperty("CamelExceptionCaught", Throwable.class);
        Throwable rootCause = ExceptionUtils.getRootCause(camelCaughtThrowable);

        String rootCauseType = rootCause != null ? rootCause.getClass().getName() : "";
        String rootCauseMessage = rootCause != null ? rootCause.getMessage() : "";

        log.warn("Exchange failed ({}: {}) . Redelivering the message locally, attempt {}/{}...", rootCauseType, rootCauseMessage, redeliveryCounter, redeliveryMaxCounter);
    }

    protected void removeAllCamelHttpHeaders(Exchange e) {
        e.getIn().removeHeaders(Constants.CAMEL_ALL_HTTP_HEADERS, GooglePubsubConstants.ACK_ID);
    }

    /**
     * Remove the PubSub synchronization.
     * This prevents an aggregator from acknowledging the aggregated PubSub messages before the end of the route.
     * In case of failure during the routing this would make it impossible to retry the messages.
     * The synchronization is stored temporarily in a header and is applied again after the aggregation is complete
     *
     * @param e
     * @see #addSynchronizationForAggregatedExchange(Exchange)
     */
    public void removeSynchronizationForAggregatedExchange(Exchange e) {
        DefaultExchange temporaryExchange = new DefaultExchange(e.getContext());
        e.getUnitOfWork().handoverSynchronization(temporaryExchange, AcknowledgeAsync.class::isInstance);
        e.getIn().setHeader(SYNCHRONIZATION_HOLDER, temporaryExchange);
    }

    /**
     * Add back the PubSub synchronization.
     *
     * @see #removeSynchronizationForAggregatedExchange(Exchange)
     */
    protected void addSynchronizationForAggregatedExchange(Exchange aggregatedExchange) {
        List<Message> messages = aggregatedExchange.getIn().getBody(List.class);
        for (Message m : messages) {
            Exchange temporaryExchange = m.getHeader(SYNCHRONIZATION_HOLDER, Exchange.class);
            if (temporaryExchange == null) {
                throw new IllegalStateException("Synchronization holder not found");
            }
            temporaryExchange.getExchangeExtension().handoverCompletions(aggregatedExchange);
        }
    }

}
