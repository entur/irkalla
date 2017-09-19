package org.rutebanken.irkalla.routes;

import org.apache.camel.Exchange;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.NotAuthenticatedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.ws.rs.NotFoundException;
import java.util.Collections;

import static org.rutebanken.irkalla.Constants.*;

@Component
public class AdminRestRouteBuilder extends BaseRouteBuilder {

    @Value("${server.admin.port}")
    public String port;

    @Value("${server.admin.host}")
    public String host;

    @Value("${authorization.enabled:true}")
    protected boolean authorizationEnabled;


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

        RestPropertyDefinition corsAllowedHeaders = new RestPropertyDefinition();
        corsAllowedHeaders.setKey("Access-Control-Allow-Headers");
        corsAllowedHeaders.setValue("Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers, Authorization");

        restConfiguration().setCorsHeaders(Collections.singletonList(corsAllowedHeaders));

        restConfiguration()
                .component("jetty")
                .bindingMode(RestBindingMode.json)
                .endpointProperty("filtersRef", "keycloakPreAuthActionsFilter,keycloakAuthenticationProcessingFilter")
                .endpointProperty("sessionSupport", "true")
                .endpointProperty("matchOnUriPrefix", "true")
                .enableCORS(true)
                .dataFormatProperty("prettyPrint", "true")
                .host(host)
                .port(port)
                .contextPath("/services")
                .apiContextPath("/stop_place_synchronization_timetable/swagger.json")
                .apiProperty("api.title", "Stop place synchronization timetable API")
                .apiProperty("api.description", "Administration of process for synchronizing stop places in the timetable database (Chouette) with the master data in the stop place registry (NSR)")
                .apiProperty("api.version", "1.0");

        rest("")
                .apiDocs(false)
                .description("Wildcard definitions necessary to get Jetty to match authorization filters to endpoints with path params")
                .get().route().routeId("admin-route-authorize-get").throwException(new NotFoundException()).endRest()
                .post().route().routeId("admin-route-authorize-post").throwException(new NotFoundException()).endRest()
                .put().route().routeId("admin-route-authorize-put").throwException(new NotFoundException()).endRest()
                .delete().route().routeId("admin-route-authorize-delete").throwException(new NotFoundException()).endRest();


        rest("/stop_place_synchronization_timetable")
                .post("/delta")
                .description("Synchronize new changes for stop places from Tiamat to Chouette")
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route().routeId("admin-chouette-synchronize-stop-places-delta")
                .process(e -> authorize(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN))
                .removeHeaders("CamelHttp*")
                .setHeader(HEADER_SYNC_OPERATION, constant(SYNC_OPERATION_DELTA))
                .inOnly("activemq:queue:ChouetteStopPlaceSyncQueue")
                .setBody(constant(null))
                .endRest()
                .post("/full")
                .description("Full synchronization of all stop places from Tiamat to Chouette")
                .param().name("cleanFirst").type(RestParamType.query).description("Whether or not not in use stop places should be deleted first").dataType("boolean").endParam()
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route().routeId("admin-chouette-synchronize-stop-places-full")
                .process(e -> authorize(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN))
                .removeHeaders("CamelHttp*")
                .choice()
                .when(simple("${header.cleanFirst}"))
                    .setHeader(HEADER_SYNC_OPERATION, constant(SYNC_OPERATION_FULL_WITH_DELETE_UNUSED_FIRST))
                .otherwise()
                    .setHeader(HEADER_SYNC_OPERATION, constant(SYNC_OPERATION_FULL))
                .end()
                .inOnly("activemq:queue:ChouetteStopPlaceSyncQueue")
                .setBody(constant(null))
                .endRest();


    }

    private void authorize(String requiredRole) {
        if (!authorizationEnabled) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new NotAuthenticatedException();
        }

        boolean authorized = false;
        if (!CollectionUtils.isEmpty(authentication.getAuthorities())) {
            authorized = authentication.getAuthorities().stream().anyMatch(authority -> requiredRole.equals(authority.getAuthority()));
        }

        if (!authorized) {
            throw new AccessDeniedException("Insufficient privileges for operation");
        }
    }
}
