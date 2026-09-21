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

package org.rutebanken.irkalla.config;

import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.jupiter.api.Test;
import org.rutebanken.irkalla.avro.EnumType;
import org.rutebanken.irkalla.avro.StopPlaceChangelogEvent;

import java.io.ByteArrayOutputStream;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AvroConfigTest {

    /**
     * Reproduces the failure seen when Avro's class security validator does not trust the generated
     * changelog classes: the Kafka Avro serializer cannot build a datum writer for the schema.
     */
    @Test
    void generatedAvroClassesAreSerializable() throws Exception {
        new AvroConfig().trustGeneratedAvroClasses();

        StopPlaceChangelogEvent event = StopPlaceChangelogEvent.newBuilder()
                .setStopPlaceId("NSR:StopPlace:1234")
                .setStopPlaceVersion(5L)
                .setStopPlaceChanged(Instant.parse("2024-01-15T10:00:00Z"))
                .setEventType(EnumType.UPDATE)
                .build();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        var writer = new SpecificDatumWriter<StopPlaceChangelogEvent>(StopPlaceChangelogEvent.getClassSchema());
        var encoder = EncoderFactory.get().binaryEncoder(out, null);
        writer.write(event, encoder);
        encoder.flush();

        assertTrue(out.size() > 0);
    }
}
