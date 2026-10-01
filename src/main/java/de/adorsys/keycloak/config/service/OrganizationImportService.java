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

import de.adorsys.keycloak.config.condition.ConditionalOnKeycloakVersion26OrNewer;
import de.adorsys.keycloak.config.model.OrganizationDomainImport;
import de.adorsys.keycloak.config.model.OrganizationIdentityProviderImport;
import de.adorsys.keycloak.config.model.OrganizationIdentityProviderLinkRepresentation;
import de.adorsys.keycloak.config.model.OrganizationImport;
import de.adorsys.keycloak.config.model.RealmImport;
import de.adorsys.keycloak.config.properties.ImportConfigProperties;
import de.adorsys.keycloak.config.repository.OrganizationRepository;
import de.adorsys.keycloak.config.repository.UserRepository;
import de.adorsys.keycloak.config.util.CloneUtil;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.MemberRepresentation;
import org.keycloak.representations.idm.OrganizationDomainRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import static de.adorsys.keycloak.config.properties.ImportConfigProperties.ImportManagedProperties.ImportManagedPropertiesValues;

@Service
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
@ConditionalOnKeycloakVersion26OrNewer
public class OrganizationImportService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationImportService.class);

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ImportConfigProperties importConfigProperties;

    public OrganizationImportService(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            ImportConfigProperties importConfigProperties
    ) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.importConfigProperties = importConfigProperties;
    }

    public void doImport(RealmImport realmImport) {
        List<OrganizationRepresentation> organizations = getOrganizations(realmImport);
        if (organizations == null || organizations.isEmpty()) return;

        String realmName = realmImport.getRealm();

        try {
            createOrUpdateOrDeleteOrganizations(realmName, organizations);
        } catch (RuntimeException e) {
            logger.warn(
                    "Failed to import organizations for realm '{}'. Error: {}",
                    realmName,
                    e.getMessage()
            );
        }
    }

    private List<OrganizationRepresentation> getOrganizations(RealmImport realmImport) {
        List<Map<String, Object>> raw = realmImport.getOrganizationsRaw();
        if (raw == null) return null;

        return raw.stream()
                .map(r -> CloneUtil.deepClone(r, OrganizationImport.class))
                .collect(Collectors.toList());
    }

    private void createOrUpdateOrDeleteOrganizations(String realmName, List<OrganizationRepresentation> organizations) {
        List<OrganizationRepresentation> existingOrganizations = organizationRepository.getAll(realmName);

        if (importConfigProperties.getManaged().getOrganization() == ImportManagedPropertiesValues.FULL) {
            deleteOrganizationsMissingInImport(realmName, organizations, existingOrganizations);
        }

        for (OrganizationRepresentation organization : organizations) {
            createOrUpdateOrganization(realmName, organization);
        }
    }

    private void deleteOrganizationsMissingInImport(
            String realmName,
            List<OrganizationRepresentation> organizations,
            List<OrganizationRepresentation> existingOrganizations
    ) {
        for (OrganizationRepresentation existingOrganization : existingOrganizations) {
            if (!hasOrganizationWithAlias(organizations, existingOrganization.getAlias())) {
                logger.debug("Delete organization '{}' in realm '{}'", existingOrganization.getAlias(), realmName);
                organizationRepository.delete(realmName, existingOrganization);
            }
        }
    }

    private void createOrUpdateOrganization(String realmName, OrganizationRepresentation organization) {
        String organizationAlias = organization.getAlias();

        if (organizationRepository.search(realmName, organizationAlias).isEmpty()) {
            logger.debug("Create organization '{}' in realm '{}'", organizationAlias, realmName);
            organizationRepository.create(realmName, withoutLinkedData(organization));
        }

        // Domain routing may only point at identity providers already linked to the organization,
        // so links are added before the organization update and removed after it.
        OrganizationImport existing = organizationRepository.getByAlias(realmName, organizationAlias);
        linkIdentityProviders(realmName, existing.getId(), organization);
        updateOrganizationIfNecessary(realmName, organization, existing);
        unlinkIdentityProviders(realmName, existing.getId(), organization);
        manageMemberships(realmName, existing.getId(), organization);
    }

    /**
     * The organization as accepted by the create endpoint: identity providers and members are
     * managed through their own endpoints, and domain routing needs linked identity providers,
     * so it is applied by the update that follows. Cloning into the plain representation drops
     * every field the admin client does not model.
     */
    private OrganizationRepresentation withoutLinkedData(OrganizationRepresentation organization) {
        return CloneUtil.deepClone(organization, OrganizationRepresentation.class, "identityProviders", "members");
    }

    private void updateOrganizationIfNecessary(
            String realmName,
            OrganizationRepresentation organization,
            OrganizationImport existingOrganization
    ) {
        OrganizationImport patched = CloneUtil.patch(existingOrganization, organization, "id", "identityProviders", "members");
        patched.setId(existingOrganization.getId());
        patched.setIdentityProviders(null);
        patched.setMembers(null);
        keepUnsetDomainRouting(patched, existingOrganization);

        if (CloneUtil.deepEquals(existingOrganization, patched, "identityProviders", "members")) {
            logger.debug("No need to update organization '{}' in realm '{}'", existingOrganization.getAlias(), realmName);
        } else {
            logger.debug("Update organization '{}' in realm '{}'", existingOrganization.getAlias(), realmName);
            organizationRepository.update(realmName, patched);
        }
    }

    /**
     * Keycloak resets the routing of every domain in an organization update that omits it. A
     * domain without routing fields in the import therefore keeps the routing the server has
     * (set in the admin console, or migrated from the identity provider config by the 26.8
     * upgrade); an empty {@code identityProviderAlias} removes it explicitly.
     */
    private void keepUnsetDomainRouting(OrganizationImport patched, OrganizationImport existing) {
        if (patched.getDomains() == null) return;

        for (OrganizationDomainRepresentation domain : patched.getDomains()) {
            if (!(domain instanceof OrganizationDomainImport routed)) continue;

            OrganizationDomainRepresentation existingDomain = existing.getDomain(domain.getName());
            OrganizationDomainImport existingRouting = existingDomain instanceof OrganizationDomainImport e ? e : null;

            if (routed.getIdentityProviderAlias() == null && existingRouting != null) {
                routed.setIdentityProviderAlias(existingRouting.getIdentityProviderAlias());
            } else if ("".equals(routed.getIdentityProviderAlias())) {
                routed.setIdentityProviderAlias(null);
            }
            if (routed.getAutoRedirect() == null && existingRouting != null) {
                routed.setAutoRedirect(existingRouting.getAutoRedirect());
            }
        }
    }

    private boolean hasOrganizationWithAlias(List<OrganizationRepresentation> organizations, String alias) {
        return organizations.stream().anyMatch(org -> Objects.equals(org.getAlias(), alias));
    }

    private void linkIdentityProviders(String realmName, String orgId, OrganizationRepresentation organization) {
        List<IdentityProviderRepresentation> idpsToAssociate = organization.getIdentityProviders();
        if (idpsToAssociate == null || idpsToAssociate.isEmpty()) return;

        Set<String> existingAliases = getLinkedAliases(realmName, orgId);

        for (IdentityProviderRepresentation idp : idpsToAssociate) {
            String idpAlias = idp.getAlias();
            if (idpAlias == null) continue;

            try {
                if (!existingAliases.contains(idpAlias)) {
                    organizationRepository.addIdentityProvider(realmName, orgId, idpAlias);
                }
                if (idp instanceof OrganizationIdentityProviderImport link) {
                    updateIdentityProviderLinkIfNecessary(realmName, orgId, organization.getAlias(), link);
                }
            } catch (NotFoundException | BadRequestException e) {
                logger.warn("Failed to associate identity provider '{}' with organization '{}': {}",
                        idpAlias, organization.getAlias(), e.getMessage());
            }
        }
    }

    /**
     * Applies {@code autoMembership} / {@code membershipType} of a link (Keycloak 26.8+). The
     * server resets an omitted setting to its default, so unset values are filled from the
     * current link before sending.
     */
    private void updateIdentityProviderLinkIfNecessary(
            String realmName, String orgId, String orgAlias, OrganizationIdentityProviderImport idp) {
        if (idp.getAutoMembership() == null && idp.getMembershipType() == null) return;

        OrganizationIdentityProviderLinkRepresentation current =
                organizationRepository.getIdentityProviderLink(realmName, orgId, idp.getAlias());
        if (current == null) {
            logger.warn("Identity provider '{}' of organization '{}' declares autoMembership/membershipType, "
                    + "but the server does not support link settings (Keycloak 26.8+). Ignored.", idp.getAlias(), orgAlias);
            return;
        }

        OrganizationIdentityProviderLinkRepresentation wanted = new OrganizationIdentityProviderLinkRepresentation(
                idp.getAutoMembership() != null ? idp.getAutoMembership() : current.getAutoMembership(),
                idp.getMembershipType() != null ? idp.getMembershipType() : current.getMembershipType()
        );

        if (Objects.equals(wanted.getAutoMembership(), current.getAutoMembership())
                && Objects.equals(wanted.getMembershipType(), current.getMembershipType())) {
            return;
        }

        logger.debug("Update link of identity provider '{}' in organization '{}'", idp.getAlias(), orgAlias);
        organizationRepository.updateIdentityProviderLink(realmName, orgId, idp.getAlias(), wanted);
    }

    private void unlinkIdentityProviders(String realmName, String orgId, OrganizationRepresentation organization) {
        if (importConfigProperties.getManaged().getOrganization() != ImportManagedPropertiesValues.FULL) return;

        Set<String> configuredAliases = organization.getIdentityProviders() == null ? Set.of()
                : organization.getIdentityProviders().stream()
                .map(IdentityProviderRepresentation::getAlias)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (String existingAlias : getLinkedAliases(realmName, orgId)) {
            if (configuredAliases.contains(existingAlias)) continue;

            try {
                organizationRepository.removeIdentityProvider(realmName, orgId, existingAlias);
            } catch (NotFoundException | BadRequestException e) {
                logger.warn("Failed to remove identity provider '{}' from organization '{}': {}",
                        existingAlias, organization.getAlias(), e.getMessage());
            }
        }
    }

    private Set<String> getLinkedAliases(String realmName, String orgId) {
        return organizationRepository.getIdentityProviders(realmName, orgId).stream()
                .map(IdentityProviderRepresentation::getAlias)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void manageMemberships(
            String realmName,
            String orgId,
            OrganizationRepresentation organization
    ) {
        List<MemberRepresentation> membersToAdd = organization.getMembers();
        List<MemberRepresentation> existingMembers = organizationRepository.getMembers(realmName, orgId);
        Set<String> existingUsernames = existingMembers.stream()
                .map(MemberRepresentation::getUsername)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (membersToAdd == null || membersToAdd.isEmpty()) {
            if (importConfigProperties.getManaged().getOrganization() == ImportManagedPropertiesValues.FULL) {
                for (MemberRepresentation existing : existingMembers) {
                    if (existing == null || existing.getUsername() == null) continue;

                    try {
                        Optional<UserRepresentation> maybeUser = userRepository.search(realmName, existing.getUsername());
                        if (maybeUser.isPresent() && maybeUser.get().getId() != null) {
                            organizationRepository.removeMember(realmName, orgId, maybeUser.get().getId());
                        }
                    } catch (NotFoundException | BadRequestException e) {
                        logger.warn("Failed to remove user '{}' from organization '{}': {}",
                                existing.getUsername(), organization.getAlias(), e.getMessage());
                    }
                }
            }
            return;
        }

        for (MemberRepresentation member : membersToAdd) {
            if (member == null || member.getUsername() == null) continue;

            String username = member.getUsername();

            try {
                Optional<UserRepresentation> maybeUser = userRepository.search(realmName, username);
                if (maybeUser.isEmpty()) {
                    logger.warn("Cannot add user '{}' to organization '{}': user not found in realm '{}'",
                            username, organization.getAlias(), realmName);
                    continue;
                }

                if (!existingUsernames.contains(username)) {
                    organizationRepository.addMember(realmName, orgId, maybeUser.get().getId());
                }
            } catch (NotFoundException | BadRequestException e) {
                logger.warn("Failed to add user '{}' to organization '{}': {}",
                        username, organization.getAlias(), e.getMessage());
            }
        }

        if (importConfigProperties.getManaged().getOrganization() == ImportManagedPropertiesValues.FULL) {
            Set<String> configuredUsernames = membersToAdd.stream()
                    .map(MemberRepresentation::getUsername)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            for (MemberRepresentation existing : existingMembers) {
                if (existing == null || existing.getUsername() == null) continue;

                if (!configuredUsernames.contains(existing.getUsername())) {
                    try {
                        Optional<UserRepresentation> maybeUser = userRepository.search(realmName, existing.getUsername());
                        if (maybeUser.isPresent() && maybeUser.get().getId() != null) {
                            organizationRepository.removeMember(realmName, orgId, maybeUser.get().getId());
                        }
                    } catch (NotFoundException | BadRequestException e) {
                        logger.warn("Failed to remove user '{}' from organization '{}': {}",
                                existing.getUsername(), organization.getAlias(), e.getMessage());
                    }
                }
            }
        }
    }
}
