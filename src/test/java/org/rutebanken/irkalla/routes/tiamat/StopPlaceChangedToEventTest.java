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

import org.junit.jupiter.api.Test;
import org.rutebanken.irkalla.domain.CrudAction;
import org.rutebanken.irkalla.domain.CrudEvent;
import org.rutebanken.irkalla.routes.tiamat.graphql.model.GraphqlGeometry;
import org.rutebanken.irkalla.routes.tiamat.graphql.model.Name;
import org.rutebanken.irkalla.routes.tiamat.graphql.model.StopPlace;
import org.rutebanken.irkalla.routes.tiamat.graphql.model.TopographicPlace;
import org.rutebanken.irkalla.routes.tiamat.graphql.model.ValidBetween;
import org.rutebanken.irkalla.routes.tiamat.mapper.StopPlaceChangedToEvent;
import org.wololo.geojson.Point;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class StopPlaceChangedToEventTest {

    private final StopPlaceChangedToEvent mapper = new StopPlaceChangedToEvent();

    @Test
    void mapsAllFieldsForCreate() {
        StopPlace current = stopPlace("NSR:StopPlace:1", 2L, "Oslo S", "onstreetBus");
        current.versionComment = "my comment";
        current.changedBy = "user1";
        current.geometry = new GraphqlGeometry("Point", List.of(10.0, 59.0));

        StopPlaceChange change = new StopPlaceChange(CrudAction.CREATE, current, null);
        CrudEvent event = mapper.toEvent(change);

        assertEquals("NSR:StopPlace:1", event.externalId);
        assertEquals(2L, event.version);
        assertEquals("Oslo S", event.name);
        assertEquals("onstreetBus", event.entityClassifier);
        assertEquals(CrudEvent.Action.CREATE, event.action);
        assertEquals(CrudEvent.EntityType.StopPlace, event.entityType);
        assertEquals("my comment", event.comment);
        assertEquals("user1", event.username);
        assertNull(event.changeType);
    }

    @Test
    void convertsPointGeometry() {
        StopPlace current = stopPlace("NSR:StopPlace:1", 1L, "Stop", null);
        current.geometry = new GraphqlGeometry("Point", List.of(10.5, 59.9));

        CrudEvent event = mapper.toEvent(new StopPlaceChange(CrudAction.CREATE, current, null));

        assertInstanceOf(Point.class, event.geometry);
        double[] coords = ((Point) event.geometry).getCoordinates();
        assertEquals(10.5, coords[0]);
        assertEquals(59.9, coords[1]);
    }

    @Test
    void nullGeometryProducesNullOnEvent() {
        StopPlace current = stopPlace("NSR:StopPlace:1", 1L, "Stop", null);
        current.geometry = null;

        CrudEvent event = mapper.toEvent(new StopPlaceChange(CrudAction.CREATE, current, null));

        assertNull(event.geometry);
    }

    @Test
    void updateSetsChangeType() {
        StopPlace current = stopPlace("NSR:StopPlace:1", 2L, "New Name", "onstreetBus");
        StopPlace previous = stopPlace("NSR:StopPlace:1", 1L, "Old Name", "onstreetBus");

        StopPlaceChange change = new StopPlaceChange(CrudAction.UPDATE, current, previous);
        CrudEvent event = mapper.toEvent(change);

        assertEquals(CrudEvent.Action.UPDATE, event.action);
        assertEquals(StopPlaceChange.StopPlaceUpdateType.NAME.toString(), event.changeType);
        assertEquals("Old Name", event.oldValue);
        assertEquals("New Name", event.newValue);
    }

    @Test
    void mapsLocationFromTopographicPlaceHierarchy() {
        StopPlace current = stopPlace("NSR:StopPlace:1", 1L, "Stop", null);
        current.topographicPlace = topographicPlace("Oslo", topographicPlace("Norway", null));

        CrudEvent event = mapper.toEvent(new StopPlaceChange(CrudAction.CREATE, current, null));

        assertEquals("Norway, Oslo", event.location);
    }

    @Test
    void multiModalStopSetsEntityClassifier() {
        StopPlace current = stopPlace("NSR:StopPlace:1", 1L, "Hub", null);
        current.__typename = StopPlaceChange.PARENT_STOP_PLACE_TYPE;

        CrudEvent event = mapper.toEvent(new StopPlaceChange(CrudAction.CREATE, current, null));

        assertEquals(StopPlaceChange.MULTI_MODAL_TYPE, event.entityClassifier);
    }

    private StopPlace stopPlace(String id, long version, String name, String type) {
        StopPlace stop = new StopPlace();
        stop.id = id;
        stop.version = version;
        stop.name = new Name(name);
        stop.stopPlaceType = type;
        stop.validBetween = new ValidBetween();
        stop.validBetween.fromDate = Instant.now().minusSeconds(3600);
        return stop;
    }

    private TopographicPlace topographicPlace(String name, TopographicPlace parent) {
        TopographicPlace place = new TopographicPlace();
        place.name = new Name(name);
        place.parentTopographicPlace = parent;
        return place;
    }
}