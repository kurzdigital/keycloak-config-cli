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

import org.keycloak.representations.idm.OrganizationDomainRepresentation;

/**
 * Organization domain with the identity provider routing introduced in Keycloak 26.8.
 * Both fields are nullable so an unset value never reaches the wire: servers before 26.8
 * reject the unknown properties, and on 26.8 an unset value means "keep what the server has".
 */
public class OrganizationDomainImport extends OrganizationDomainRepresentation {

    private String identityProviderAlias;
    private Boolean autoRedirect;

    public String getIdentityProviderAlias() {
        return identityProviderAlias;
    }

    public void setIdentityProviderAlias(String identityProviderAlias) {
        this.identityProviderAlias = identityProviderAlias;
    }

    public Boolean getAutoRedirect() {
        return autoRedirect;
    }

    public void setAutoRedirect(Boolean autoRedirect) {
        this.autoRedirect = autoRedirect;
    }
}
