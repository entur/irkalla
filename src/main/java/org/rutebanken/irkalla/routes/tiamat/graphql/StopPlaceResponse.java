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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.rutebanken.irkalla.routes.tiamat.graphql.model.StopPlace;
import org.springframework.util.CollectionUtils;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StopPlaceResponse {


    public Data data;

    public StopPlaceResponse() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Data {
        public List<StopPlace> current;

        public List<StopPlace> previous;
    }


    @JsonIgnore
    public StopPlace getCurrent() {
        if (data == null || CollectionUtils.isEmpty(data.current)) {
            return null;
        }
        return data.current.getFirst();
    }

    @JsonIgnore
    public StopPlace getPreviousVersion() {
        if (data == null || CollectionUtils.isEmpty(data.previous)) {
            return null;
        }
        return data.previous.getFirst();
    }
}

