/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2021 adorsys GmbH & Co. KG @ https://adorsys.com
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

package de.adorsys.keycloak.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.adorsys.keycloak.config.AbstractImportIT;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Keycloak 26.8 moved identity provider routing onto organization domains
 * ({@code identityProviderAlias}, {@code autoRedirect}) and added membership settings per
 * identity provider link. The pinned admin client models neither, so the assertions read the
 * server state as raw JSON.
 */
@TestPropertySource(properties = {
        "import.managed.organization=full"
})
class ImportOrganizationDomainRoutingIT extends AbstractImportIT {
    private static final String REALM_NAME = "org-feature-test";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    ImportOrganizationDomainRoutingIT() {
        this.resourcePath = "import-files/organizations";
    }

    @Test
    void shouldManageDomainRoutingAndLinkSettings() throws Exception {
        assumeTrue(KEYCLOAK_VERSION.compareTo("26.8") >= 0, "domain routing exists since Keycloak 26.8");

        // routing set outside of keycloak-config-cli survives an import that does not mention it
        doImport("06_import_organizations_full_realm.json");
        String orgId = organizationId("acme");
        ObjectNode org = (ObjectNode) get("/organizations/" + orgId);
        for (JsonNode domain : org.get("domains")) {
            if ("acme.com".equals(domain.get("name").asText())) {
                ((ObjectNode) domain).put("identityProviderAlias", "github");
                ((ObjectNode) domain).put("autoRedirect", true);
            }
        }
        send("PUT", "/organizations/" + orgId, org);

        doImport("12_update_organization_without_routing.json");
        assertThat(get("/organizations/" + orgId).get("description").asText(), is("Main organization for Acme Corporation (changed)"));
        assertRouting(orgId, "github", true);

        // routing and link settings set by keycloak-config-cli
        doImport("13_remove_domain_routing.json");
        assertRouting(orgId, null, false);

        doImport("11_set_domain_routing_and_link_settings.json");
        assertRouting(orgId, "github", true);
        JsonNode link = get("/organizations/" + orgId + "/identity-providers/github").get("organizationLinks").get(0);
        assertThat(link.get("membershipType").asText(), is("MANAGED"));
        assertThat(link.get("autoMembership").asBoolean(), is(true));

        // link settings not mentioned in the import are kept as well
        doImport("12_update_organization_without_routing.json");
        assertRouting(orgId, "github", true);
        link = get("/organizations/" + orgId + "/identity-providers/github").get("organizationLinks").get(0);
        assertThat(link.get("membershipType").asText(), is("MANAGED"));
    }

    private void assertRouting(String orgId, String identityProviderAlias, boolean autoRedirect) throws Exception {
        for (JsonNode domain : get("/organizations/" + orgId).get("domains")) {
            if ("acme.com".equals(domain.get("name").asText())) {
                assertThat(domain.path("identityProviderAlias").asText(null), is(identityProviderAlias));
                assertThat(domain.path("autoRedirect").asBoolean(), is(autoRedirect));
                return;
            }
        }
        throw new AssertionError("domain acme.com not found");
    }

    private String organizationId(String alias) throws Exception {
        for (JsonNode org : get("/organizations?max=100")) {
            if (alias.equals(org.path("alias").asText())) {
                return org.get("id").asText();
            }
        }
        throw new IllegalStateException("organization " + alias + " not found");
    }

    private JsonNode get(String path) throws Exception {
        return send("GET", path, null);
    }

    private JsonNode send(String method, String path, JsonNode body) throws Exception {
        String base = System.getProperty("keycloak.url").replaceAll("/$", "");
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/admin/realms/" + REALM_NAME + path))
                .header("Authorization", "Bearer " + keycloakProvider.getInstance().tokenManager().getAccessTokenString())
                .header("Content-Type", "application/json")
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(method + " " + path + " -> " + response.body(), response.statusCode() / 100, is(2));
        return response.body().isEmpty() ? MAPPER.nullNode() : MAPPER.readTree(response.body());
    }
}
