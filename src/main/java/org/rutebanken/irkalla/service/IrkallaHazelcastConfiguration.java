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

package org.rutebanken.irkalla.service;

import org.rutebanken.hazelcasthelper.service.KubernetesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
public class IrkallaHazelcastConfiguration extends KubernetesService {
    private static final Logger log = LoggerFactory.getLogger(IrkallaHazelcastConfiguration.class);

    public IrkallaHazelcastConfiguration(@Value("${rutebanken.kubernetes.url:}") String kubernetesUrl,
                                          @Value("${rutebanken.kubernetes.namespace:default}") String namespace,
                                          @Value("${rutebanken.kubernetes.enabled:true}") boolean kubernetesEnabled) {
        super(kubernetesUrl, namespace, kubernetesEnabled);
    }


}
