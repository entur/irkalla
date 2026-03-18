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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.rutebanken.irkalla.domain.CrudAction;
import org.rutebanken.irkalla.routes.tiamat.graphql.GraphQLStopPlaceDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {GraphQLStopPlaceDao.class, GraphqlStopPlaceDaoTest.TestConfig.class})
@TestPropertySource(properties = {
        "tiamat.url=http://tiamat-test",
        "tiamat.graphql.path=/services/stop_places/graphql",
        "http.client.name=irkalla",
        "HOSTNAME=irkalla"
})
class GraphqlStopPlaceDaoTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        RestTemplate restTemplate() {
            return new RestTemplate();
        }
    }

    private static final String TIAMAT_GRAPHQL_URL = "http://tiamat-test/services/stop_places/graphql";

    @Autowired
    private GraphQLStopPlaceDao stopPlaceDao;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        server = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void testGetStopPlaceChange_returnsCurrentAndPrevious() {
        server.expect(requestTo(TIAMAT_GRAPHQL_URL))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "current": [{"id": "NSR:StopPlace:3512", "version": 3, "stopPlaceType": "onstreetBus"}],
                            "previous": [{"id": "NSR:StopPlace:3512", "version": 2, "stopPlaceType": "onstreetBus"}]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        StopPlaceChange result = stopPlaceDao.getStopPlaceChange(CrudAction.UPDATE, "NSR:StopPlace:3512", 3L);

        assertNotNull(result);
        assertEquals("NSR:StopPlace:3512", result.getCurrent().id);
        assertEquals(3L, result.getCurrent().version);
        assertNotNull(result.getPreviousVersion());
        assertEquals(2L, result.getPreviousVersion().version);
    }

    @Test
    void testGetStopPlaceChange_returnsNullWhenStopPlaceNotFound() {
        server.expect(requestTo(TIAMAT_GRAPHQL_URL))
                .andRespond(withSuccess("""
                        {"data": {"current": [], "previous": []}}
                        """, MediaType.APPLICATION_JSON));

        StopPlaceChange result = stopPlaceDao.getStopPlaceChange(CrudAction.CREATE, "NSR:StopPlace:9999", 1L);

        assertNull(result);
    }

    @Test
    void testGetStopPlaceChange_noPreviousVersionForVersion1() {
        server.expect(requestTo(TIAMAT_GRAPHQL_URL))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "current": [{"id": "NSR:StopPlace:3512", "version": 1}],
                            "previous": [{"id": "NSR:StopPlace:3512", "version": 1}]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        // Tiamat returns v1 when queried for v0 — the DAO must discard it
        StopPlaceChange result = stopPlaceDao.getStopPlaceChange(CrudAction.CREATE, "NSR:StopPlace:3512", 1L);

        assertNotNull(result);
        assertNotNull(result.getCurrent());
        assertNull(result.getPreviousVersion());
    }
}