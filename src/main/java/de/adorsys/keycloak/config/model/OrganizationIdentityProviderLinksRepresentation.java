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


package de.adorsys.keycloak.config.model;

import java.util.List;

/**
 * The part of an organization's identity provider (Keycloak 26.8+) that describes its
 * organization links. Every other property of the response is ignored.
 */
public class OrganizationIdentityProviderLinksRepresentation {

    private String alias;
    private List<OrganizationIdentityProviderLinkRepresentation> organizationLinks;

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public List<OrganizationIdentityProviderLinkRepresentation> getOrganizationLinks() {
        return organizationLinks;
    }

    public void setOrganizationLinks(List<OrganizationIdentityProviderLinkRepresentation> organizationLinks) {
        this.organizationLinks = organizationLinks;
    }
}
