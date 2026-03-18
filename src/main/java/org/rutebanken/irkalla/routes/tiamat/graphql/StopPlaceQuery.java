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

package org.rutebanken.irkalla.routes.tiamat.graphql;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StopPlaceQuery {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Query size 1 should be sufficient when querying for fixed id and version
    private static final int DEFAULT_QUERY_SIZE = 1;

    private static final String RESULT_DEFINITION = """
            {
              id
              version
              validBetween { fromDate toDate }
              name { value }
              geometry { type coordinates }
              __typename
              topographicPlace {
                topographicPlaceType
                name { value }
                parentTopographicPlace {
                  topographicPlaceType
                  name { value }
                }
              }
              versionComment
              changedBy
              ... on StopPlace {
                stopPlaceType
                quays {
                  id
                  name { value }
                  geometry { type coordinates }
                }
              }
            }""";

    public String operationName = "findStop";

    public Map<String, Object> variables = new HashMap<>();

    public String query = """
            query stopPlace($id: String, $size: Int, $currentVersion: Int, $previousVersion: Int) {
              current: stopPlace(id: $id, size: $size, version: $currentVersion) %s
              previous: stopPlace(id: $id, size: $size, version: $previousVersion) %s
            }""".formatted(RESULT_DEFINITION, RESULT_DEFINITION);

    public StopPlaceQuery(String stopPlaceId, Long version) {
        variables.put("id", stopPlaceId);
        variables.put("currentVersion", version);
        variables.put("previousVersion", version - 1);
        setQuerySize(DEFAULT_QUERY_SIZE);
    }

    public void setQuerySize(int querySize) {
        variables.put("size", querySize);
    }

    @Override
    public String toString() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}