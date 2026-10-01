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

/**
 * Link between an organization and an identity provider (Keycloak 26.8+), as returned in
 * {@code organizationLinks} and accepted by
 * {@code PUT /admin/realms/{realm}/organizations/{id}/identity-providers/{alias}}.
 */
public class OrganizationIdentityProviderLinkRepresentation {

    private String organizationId;
    private Boolean autoMembership;
    private String membershipType;

    public OrganizationIdentityProviderLinkRepresentation() {
    }

    public OrganizationIdentityProviderLinkRepresentation(Boolean autoMembership, String membershipType) {
        this.autoMembership = autoMembership;
        this.membershipType = membershipType;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public Boolean getAutoMembership() {
        return autoMembership;
    }

    public void setAutoMembership(Boolean autoMembership) {
        this.autoMembership = autoMembership;
    }

    public String getMembershipType() {
        return membershipType;
    }

    public void setMembershipType(String membershipType) {
        this.membershipType = membershipType;
    }
}
