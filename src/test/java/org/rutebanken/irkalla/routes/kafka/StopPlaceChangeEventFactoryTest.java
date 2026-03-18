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

package org.rutebanken.irkalla.routes.kafka;

import org.junit.jupiter.api.Test;
import org.rutebanken.irkalla.avro.EnumType;
import org.rutebanken.irkalla.avro.StopPlaceChangelogEvent;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StopPlaceChangeEventFactoryTest {

    private final StopPlaceChangeEventFactory factory = new StopPlaceChangeEventFactory();

    @Test
    void createsEventWithAllFields() {
        long changedEpoch = Instant.parse("2024-01-15T10:00:00Z").toEpochMilli();

        StopPlaceChangelogEvent event = factory.createStopPlaceChangelogEvent(
                "NSR:StopPlace:1234", 5L, changedEpoch, EnumType.UPDATE);

        assertEquals("NSR:StopPlace:1234", event.getStopPlaceId());
        assertEquals(5L, event.getStopPlaceVersion());
        assertEquals(Instant.ofEpochMilli(changedEpoch), event.getStopPlaceChanged());
        assertEquals(EnumType.UPDATE, event.getEventType());
    }

    @Test
    void nullStopPlaceChangedIsAllowed() {
        StopPlaceChangelogEvent event = factory.createStopPlaceChangelogEvent(
                "NSR:StopPlace:1", 1L, null, EnumType.CREATE);

        assertNull(event.getStopPlaceChanged());
    }

    @Test
    void nullStopPlaceIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                factory.createStopPlaceChangelogEvent(null, 1L, null, EnumType.CREATE));
    }

    @Test
    void nullStopPlaceVersionThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                factory.createStopPlaceChangelogEvent("NSR:StopPlace:1", null, null, EnumType.CREATE));
    }

    @Test
    void nullEventTypeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                factory.createStopPlaceChangelogEvent("NSR:StopPlace:1", 1L, null, null));
    }
}