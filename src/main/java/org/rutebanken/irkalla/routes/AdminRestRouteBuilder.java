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

import jakarta.ws.rs.NotFoundException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.rutebanken.helper.organisation.NotAuthenticatedException;
import org.rutebanken.irkalla.security.IrkallaAuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import static org.rutebanken.irkalla.Constants.HEADER_SYNC_OPERATION;
import static org.rutebanken.irkalla.Constants.SYNC_OPERATION_DELTA;
import static org.rutebanken.irkalla.Constants.SYNC_OPERATION_FULL;
import static org.rutebanken.irkalla.Constants.SYNC_OPERATION_FULL_WITH_DELETE_UNUSED_FIRST;

@Component
public class AdminRestRouteBuilder extends BaseRouteBuilder {

    private final IrkallaAuthorizationService irkallaAuthorizationService;

    public AdminRestRouteBuilder(IrkallaAuthorizationService irkallaAuthorizationService) {
        this.irkallaAuthorizationService = irkallaAuthorizationService;
    }


    @Override
    public void configure() throws Exception {
        super.configure();

        onException(AccessDeniedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(403))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        onException(NotAuthenticatedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        onException(NotFoundException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        restConfiguration()
                .component("servlet")
                .contextPath("/services")
                .bindingMode(RestBindingMode.json)
                .endpointProperty("matchOnUriPrefix", "true")
                .dataFormatProperty("prettyPrint", "true")
                .apiContextPath("/stop_place_synchronization_timetable/swagger.json")
                .apiProperty("api.title", "Stop place synchronization timetable API")
                .apiProperty("api.description", "Administration of process for synchronizing stop places in the timetable database (Chouette) with the master data in the stop place registry (NSR)")
                .apiProperty("api.version", "1.0");

        rest("")
                .apiDocs(false)
                .description("Wildcard definitions necessary to get Jetty to match authorization filters to endpoints with path params")
                .get()
                .to("direct:adminRouteAuthorizeGet")
                .post()
                .to("direct:adminRouteAuthorizePost")
                .put()
                .to("direct:adminRouteAuthorizePut")
                .delete()
                .to("direct:adminRouteAuthorizeDelete");


        rest("/stop_place_synchronization_timetable")
                .get("/status")
                .bindingMode(RestBindingMode.off)
                .description("Get time for which synchronization is up to date")
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .to("direct:adminChouetteSynchronizeStopPlacesStatus")

                .post("/delta")
                .description("Synchronize new changes for stop places from Tiamat to Chouette")
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .to("direct:adminChouetteSynchronizeStopPlacesDelta")

                .post("/full")
                .description("Full synchronization of all stop places from Tiamat to Chouette")
                .param().name("cleanFirst").type(RestParamType.query).description("Whether or not not in use stop places should be deleted first").dataType("boolean").endParam()
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .to("direct:adminChouetteSynchronizeStopPlacesFull");

        from("direct:adminChouetteSynchronizeStopPlacesStatus")
                .process(this::removeAllCamelHttpHeaders)
                .to("direct:getSyncStatusUntilTime")
                .routeId("admin-chouette-synchronize-stop-places-status");

        from("direct:adminChouetteSynchronizeStopPlacesDelta")
                .process(e -> irkallaAuthorizationService.verifyAdministratorPrivileges())
                .process(this::removeAllCamelHttpHeaders)
                .setHeader(HEADER_SYNC_OPERATION, constant(SYNC_OPERATION_DELTA))
                .setBody(constant(""))
                .to(ExchangePattern.InOnly,"google-pubsub:{{irkalla.pubsub.project.id}}:ChouetteStopPlaceSyncQueue")
                .routeId("admin-chouette-synchronize-stop-places-delta");

        from("direct:adminChouetteSynchronizeStopPlacesFull")
                .process(e -> irkallaAuthorizationService.verifyAdministratorPrivileges())
                .removeHeaders("CamelHttp*")
                .choice()
                .when(simple("${header.cleanFirst}"))
                .setHeader(HEADER_SYNC_OPERATION, constant(SYNC_OPERATION_FULL_WITH_DELETE_UNUSED_FIRST))
                .otherwise()
                .setHeader(HEADER_SYNC_OPERATION, constant(SYNC_OPERATION_FULL))
                .end()
                .setBody(constant(""))
                .to(ExchangePattern.InOnly,"google-pubsub:{{irkalla.pubsub.project.id}}:ChouetteStopPlaceSyncQueue")
                .routeId("admin-chouette-synchronize-stop-places-full");



        from("direct:adminRouteAuthorizeGet")
                .throwException(new NotFoundException())
                .routeId("admin-route-authorize-get");

        from("direct:adminRouteAuthorizePost")
                .throwException(new NotFoundException())
                .routeId("admin-route-authorize-post");

        from("direct:adminRouteAuthorizePut")
                .throwException(new NotFoundException())
                .routeId("admin-route-authorize-put");

        from("direct:adminRouteAuthorizeDelete")
                .throwException(new NotFoundException())
                .routeId("admin-route-authorize-delete");


    }
}
