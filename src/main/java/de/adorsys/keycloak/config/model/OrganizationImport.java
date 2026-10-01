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

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.OrganizationDomainRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;

import java.util.List;
import java.util.Set;

/**
 * Organization with the Keycloak 26.8 fields the pinned admin client does not model yet:
 * identity provider routing per domain and membership settings per identity provider link.
 * The overrides only redirect Jackson to the extended element types; every mapper that
 * clones or deserializes an instance of this class keeps the extra fields.
 */
public class OrganizationImport extends OrganizationRepresentation {

    @Override
    @JsonDeserialize(contentAs = OrganizationDomainImport.class)
    public Set<OrganizationDomainRepresentation> getDomains() {
        return super.getDomains();
    }

    @Override
    @JsonDeserialize(contentAs = OrganizationIdentityProviderImport.class)
    public List<IdentityProviderRepresentation> getIdentityProviders() {
        return super.getIdentityProviders();
    }

    @Override
    @JsonSetter("identityProviders")
    @JsonDeserialize(contentAs = OrganizationIdentityProviderImport.class)
    public void setIdentityProviders(List<IdentityProviderRepresentation> identityProviders) {
        super.setIdentityProviders(identityProviders);
    }
}
