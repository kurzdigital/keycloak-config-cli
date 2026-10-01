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


package de.adorsys.keycloak.config.resource;

import de.adorsys.keycloak.config.model.OrganizationIdentityProviderLinkRepresentation;
import de.adorsys.keycloak.config.model.OrganizationIdentityProviderLinksRepresentation;
import de.adorsys.keycloak.config.model.OrganizationImport;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * JAX-RS proxy interface for the parts of the Keycloak Organizations REST API
 * (/admin/realms/{realm}/organizations) whose representations changed in Keycloak 26.8
 * beyond what the pinned admin client models.
 */
public interface OrganizationsCompatResource {

    @GET
    @Path("/admin/realms/{realm}/organizations/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    OrganizationImport getOrganization(@PathParam("realm") String realm, @PathParam("id") String id);

    @PUT
    @Path("/admin/realms/{realm}/organizations/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    Response updateOrganization(
            @PathParam("realm") String realm,
            @PathParam("id") String id,
            OrganizationImport organization);

    @GET
    @Path("/admin/realms/{realm}/organizations/{id}/identity-providers/{alias}")
    @Produces(MediaType.APPLICATION_JSON)
    OrganizationIdentityProviderLinksRepresentation getIdentityProvider(
            @PathParam("realm") String realm,
            @PathParam("id") String id,
            @PathParam("alias") String alias);

    @PUT
    @Path("/admin/realms/{realm}/organizations/{id}/identity-providers/{alias}")
    @Consumes(MediaType.APPLICATION_JSON)
    Response updateIdentityProviderLink(
            @PathParam("realm") String realm,
            @PathParam("id") String id,
            @PathParam("alias") String alias,
            OrganizationIdentityProviderLinkRepresentation link);
}
