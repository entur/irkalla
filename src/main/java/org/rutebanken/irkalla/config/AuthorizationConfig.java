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
 *
 */

package org.rutebanken.irkalla.config;

import org.entur.oauth2.AuthorizedWebClientBuilder;
import org.entur.oauth2.JwtRoleAssignmentExtractor;
import org.entur.ror.permission.RemoteBabaRoleAssignmentExtractor;
import org.rutebanken.helper.organisation.RoleAssignmentExtractor;
import org.rutebanken.helper.organisation.authorization.AuthorizationService;
import org.rutebanken.helper.organisation.authorization.DefaultAuthorizationService;
import org.rutebanken.helper.organisation.authorization.FullAccessAuthorizationService;
import org.rutebanken.irkalla.security.IrkallaAuthorizationService;
import org.rutebanken.irkalla.security.DefaultIrkallaAuthorizationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configure authorization.
 */
@Configuration
public class AuthorizationConfig {

    @ConditionalOnProperty(
            value = "irkalla.security.role.assignment.extractor",
            havingValue = "jwt",
            matchIfMissing = true
    )
    @Bean
    public RoleAssignmentExtractor jwtRoleAssignmentExtractor() {
        return new JwtRoleAssignmentExtractor();
    }

    @ConditionalOnProperty(
            value = "irkalla.security.role.assignment.extractor",
            havingValue = "baba"
    )
    @Bean
    public RoleAssignmentExtractor babaRoleAssignmentExtractor(
            @Qualifier("internalWebClient") WebClient webClient,
            @Value("${user.permission.rest.service.url}") String url
    ) {
        return new RemoteBabaRoleAssignmentExtractor(webClient, url);
    }

    @ConditionalOnProperty(
            value = "irkalla.security.role.assignment.extractor",
            havingValue = "baba"
    )
    @Bean("internalWebClient")
    WebClient internalWebClient(
            WebClient.Builder webClientBuilder,
            OAuth2ClientProperties properties,
            @Value("${ror.oauth2.client.audience}") String audience
    ) {
        return new AuthorizedWebClientBuilder(webClientBuilder)
                .withOAuth2ClientProperties(properties)
                .withAudience(audience)
                .withClientRegistrationId("internal")
                .build();
    }


    @ConditionalOnProperty(
            value = "irkalla.security.authorization-service",
            havingValue = "token-based"
    )
    @Bean("authorizationService")
    public AuthorizationService<Long> tokenBasedAuthorizationService(RoleAssignmentExtractor roleAssignmentExtractor) {
        return new DefaultAuthorizationService<>(roleAssignmentExtractor);
    }

    @ConditionalOnProperty(
            value = "irkalla.security.authorization-service",
            havingValue = "full-access"
    )
    @Bean("authorizationService")
    public AuthorizationService<Long> fullAccessAuthorizationService() {
        return new FullAccessAuthorizationService();
    }


    @Bean
    public IrkallaAuthorizationService irkallaAuthorizationService(AuthorizationService<Long> authorizationService) {
        return new DefaultIrkallaAuthorizationService(authorizationService);
    }

}


