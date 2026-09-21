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

import jakarta.annotation.PostConstruct;
import org.apache.avro.util.ClassSecurityValidator;
import org.rutebanken.irkalla.avro.StopPlaceChangelogEvent;
import org.springframework.context.annotation.Configuration;

/**
 * Avro 1.12.2 refuses to load any class referenced by a schema unless it is explicitly trusted,
 * which breaks the Avro serialization of the generated changelog classes.
 * Trust the package holding the classes generated from src/main/avro in addition to Avro's own defaults.
 */
@Configuration
public class AvroConfig {

    private static final String GENERATED_AVRO_PACKAGE_PREFIX = StopPlaceChangelogEvent.class.getPackageName() + ".";

    @PostConstruct
    void trustGeneratedAvroClasses() {
        ClassSecurityValidator.setGlobal(
                ClassSecurityValidator.composite(
                        ClassSecurityValidator.DEFAULT,
                        clazz -> clazz.getName().startsWith(GENERATED_AVRO_PACKAGE_PREFIX)
                )
        );
    }
}
