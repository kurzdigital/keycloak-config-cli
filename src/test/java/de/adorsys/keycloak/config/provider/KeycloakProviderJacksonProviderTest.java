/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2025 adorsys GmbH & Co. KG @ https://adorsys.com
 * ---
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---license-end
 */

package de.adorsys.keycloak.config.provider;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

import jakarta.ws.rs.core.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakProviderJacksonProviderTest {

    @Test
    void shouldDisableFailOnUnknownProperties() {
        KeycloakProvider.JacksonProvider provider = new KeycloakProvider.JacksonProvider();

        ObjectMapper mapper = provider.locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);

        assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    /**
     * The Keycloak server rejects a request body containing a property it does not know
     * ("Unrecognized field ...") regardless of that property's value. The admin client's
     * representations may model fields newer than the server, so every unset field must be
     * omitted from the wire — exactly what the official admin-client JacksonProvider does with
     * {@code NON_NULL} — instead of being sent as an explicit {@code null}.
     */
    @Test
    void shouldOmitNullPropertiesWhenSerializing() throws Exception {
        KeycloakProvider.JacksonProvider provider = new KeycloakProvider.JacksonProvider();
        ObjectMapper mapper = provider.locateMapper(RealmRepresentation.class, MediaType.APPLICATION_JSON_TYPE);

        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm("only-this");

        JsonNode json = mapper.readTree(mapper.writeValueAsString(realm));

        assertEquals(1, json.size(), "unset properties must not be serialized: " + json);
        assertTrue(json.has("realm"));
    }

    /**
     * Map entries with a null value must survive: ClientImportService clears
     * authenticationFlowBindingOverrides by sending the existing keys with null values,
     * which the server interprets as "remove this override".
     */
    @Test
    void shouldKeepNullMapValuesWhenSerializing() throws Exception {
        KeycloakProvider.JacksonProvider provider = new KeycloakProvider.JacksonProvider();
        ObjectMapper mapper = provider.locateMapper(ClientRepresentation.class, MediaType.APPLICATION_JSON_TYPE);

        ClientRepresentation client = new ClientRepresentation();
        Map<String, String> overrides = new HashMap<>();
        overrides.put("browser", null);
        client.setAuthenticationFlowBindingOverrides(overrides);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(client));

        JsonNode serialized = json.get("authenticationFlowBindingOverrides");
        assertTrue(serialized != null && serialized.has("browser"), "null map entry must be serialized: " + json);
        assertTrue(serialized.get("browser").isNull());
    }
}
