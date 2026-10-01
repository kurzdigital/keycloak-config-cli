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

package de.adorsys.keycloak.config.service;

import de.adorsys.keycloak.config.model.OrganizationDomainImport;
import de.adorsys.keycloak.config.model.OrganizationIdentityProviderLinkRepresentation;
import de.adorsys.keycloak.config.model.OrganizationImport;
import de.adorsys.keycloak.config.model.RealmImport;
import de.adorsys.keycloak.config.properties.ImportConfigProperties;
import de.adorsys.keycloak.config.repository.OrganizationRepository;
import de.adorsys.keycloak.config.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.MemberRepresentation;
import org.keycloak.representations.idm.OrganizationDomainRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.adorsys.keycloak.config.properties.ImportConfigProperties.ImportManagedProperties.ImportManagedPropertiesValues;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationImportServiceTest {

    private OrganizationRepository organizationRepository;
    private UserRepository userRepository;
    private ImportConfigProperties importConfigProperties;
    private ImportConfigProperties.ImportManagedProperties managedProperties;

    private OrganizationImportService service;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        userRepository = mock(UserRepository.class);
        importConfigProperties = mock(ImportConfigProperties.class);
        managedProperties = mock(ImportConfigProperties.ImportManagedProperties.class);

        when(importConfigProperties.getManaged()).thenReturn(managedProperties);
        when(managedProperties.getOrganization()).thenReturn(ImportManagedPropertiesValues.NO_DELETE);

        service = new OrganizationImportService(organizationRepository, userRepository, importConfigProperties);
    }

    @Test
    void doImport_shouldReturnWhenOrganizationsRawIsNull() {
        RealmImport realmImport = new RealmImport();
        realmImport.setRealm("test");

        service.doImport(realmImport);

        verify(organizationRepository, never()).getAll(anyString());
        verify(organizationRepository, never()).create(anyString(), any());
    }

    @Test
    void doImport_shouldReturnWhenOrganizationsIsEmpty() {
        RealmImport realmImport = new RealmImport();
        realmImport.setRealm("test");
        realmImport.setOrganizationsRaw(Collections.emptyList());

        service.doImport(realmImport);

        verify(organizationRepository, never()).getAll(anyString());
        verify(organizationRepository, never()).create(anyString(), any());
    }

    @Test
    void doImport_fullManaged_shouldDeleteOrganizationsMissingInImport() {
        when(managedProperties.getOrganization()).thenReturn(ImportManagedPropertiesValues.FULL);

        RealmImport realmImport = new RealmImport();
        realmImport.setRealm("test");

        Map<String, Object> imported = new HashMap<>();
        imported.put("alias", "org-a");
        realmImport.setOrganizationsRaw(List.of(imported));

        OrganizationRepresentation existing = new OrganizationRepresentation();
        existing.setAlias("org-to-delete");

        when(organizationRepository.getAll("test")).thenReturn(List.of(existing));
        when(organizationRepository.search(eq("test"), eq("org-a"))).thenReturn(Optional.empty());

        OrganizationImport created = new OrganizationImport();
        created.setAlias("org-a");
        created.setId("org-a-id");
        when(organizationRepository.getByAlias(eq("test"), eq("org-a"))).thenReturn(created);

        when(organizationRepository.getIdentityProviders(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());
        when(organizationRepository.getMembers(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());

        service.doImport(realmImport);

        verify(organizationRepository, times(1)).delete(eq("test"), eq(existing));
        verify(organizationRepository, times(1)).create(eq("test"), any(OrganizationRepresentation.class));
    }

    @Test
    void doImport_fullManaged_shouldRemoveMembersMissingFromImport() {
        when(managedProperties.getOrganization()).thenReturn(ImportManagedPropertiesValues.FULL);

        RealmImport realmImport = new RealmImport();
        realmImport.setRealm("test");

        Map<String, Object> imported = new HashMap<>();
        imported.put("alias", "org-a");
        // members intentionally omitted => empty/null => triggers removal of existing members when FULL
        realmImport.setOrganizationsRaw(List.of(imported));

        when(organizationRepository.getAll("test")).thenReturn(Collections.emptyList());
        when(organizationRepository.search(eq("test"), eq("org-a"))).thenReturn(Optional.empty());

        OrganizationImport created = new OrganizationImport();
        created.setAlias("org-a");
        created.setId("org-a-id");
        when(organizationRepository.getByAlias(eq("test"), eq("org-a"))).thenReturn(created);

        when(organizationRepository.getIdentityProviders(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());

        MemberRepresentation existingMember = new MemberRepresentation();
        existingMember.setUsername("bob");
        when(organizationRepository.getMembers(eq("test"), eq("org-a-id"))).thenReturn(List.of(existingMember));

        UserRepresentation user = new UserRepresentation();
        user.setId("bob-id");
        when(userRepository.search(eq("test"), eq("bob"))).thenReturn(Optional.of(user));

        service.doImport(realmImport);

        verify(organizationRepository, times(1)).removeMember(eq("test"), eq("org-a-id"), eq("bob-id"));
    }

    @Test
    void doImport_shouldUpdateExistingOrganizationAndManageIdpsAndMembers() {
        RealmImport realmImport = new RealmImport();
        realmImport.setRealm("test");

        Map<String, Object> imported = new HashMap<>();
        imported.put("alias", "org-a");
        imported.put("description", "new-desc");
        imported.put("identityProviders", List.of(Map.of("alias", "idp-a")));
        imported.put("members", List.of(Map.of("username", "alice")));
        realmImport.setOrganizationsRaw(List.of(imported));

        OrganizationRepresentation existing = new OrganizationRepresentation();
        existing.setId("org-a-id");
        existing.setAlias("org-a");
        existing.setDescription("old-desc");
        when(organizationRepository.search(eq("test"), eq("org-a"))).thenReturn(Optional.of(existing));

        OrganizationImport resolved = new OrganizationImport();
        resolved.setId("org-a-id");
        resolved.setAlias("org-a");
        when(organizationRepository.getByAlias(eq("test"), eq("org-a"))).thenReturn(resolved);

        IdentityProviderRepresentation idpToAdd = new IdentityProviderRepresentation();
        idpToAdd.setAlias("idp-a");
        when(organizationRepository.getIdentityProviders(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());

        MemberRepresentation member = new MemberRepresentation();
        member.setUsername("alice");
        when(organizationRepository.getMembers(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());

        UserRepresentation alice = new UserRepresentation();
        alice.setId("alice-id");
        when(userRepository.search(eq("test"), eq("alice"))).thenReturn(Optional.of(alice));

        service.doImport(realmImport);

        verify(organizationRepository, times(1)).update(eq("test"), any(OrganizationImport.class));
        verify(organizationRepository, times(1)).addIdentityProvider(eq("test"), eq("org-a-id"), eq("idp-a"));
        verify(organizationRepository, times(1)).addMember(eq("test"), eq("org-a-id"), eq("alice-id"));
    }

    @Test
    void doImport_fullManaged_shouldRemoveIdpsWhenImportHasNone() {
        when(managedProperties.getOrganization()).thenReturn(ImportManagedPropertiesValues.FULL);

        RealmImport realmImport = new RealmImport();
        realmImport.setRealm("test");

        Map<String, Object> imported = new HashMap<>();
        imported.put("alias", "org-a");
        realmImport.setOrganizationsRaw(List.of(imported));

        when(organizationRepository.getAll("test")).thenReturn(Collections.emptyList());
        when(organizationRepository.search(eq("test"), eq("org-a"))).thenReturn(Optional.empty());

        OrganizationImport created = new OrganizationImport();
        created.setAlias("org-a");
        created.setId("org-a-id");
        when(organizationRepository.getByAlias(eq("test"), eq("org-a"))).thenReturn(created);

        IdentityProviderRepresentation existingIdp = new IdentityProviderRepresentation();
        existingIdp.setAlias("idp-old");
        when(organizationRepository.getIdentityProviders(eq("test"), eq("org-a-id"))).thenReturn(List.of(existingIdp));
        when(organizationRepository.getMembers(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());

        service.doImport(realmImport);

        verify(organizationRepository, times(1)).removeIdentityProvider(eq("test"), eq("org-a-id"), eq("idp-old"));
    }

    @Test
    void doImport_fullManaged_shouldRemoveIdentityProvidersMissingFromImport() {
        when(managedProperties.getOrganization()).thenReturn(ImportManagedPropertiesValues.FULL);

        RealmImport realmImport = new RealmImport();
        realmImport.setRealm("test");

        realmImport.setOrganizationsRaw(List.of(
                Map.of(
                        "alias", "org-a",
                        "identityProviders", List.of(Map.of("alias", "idp-keep"))
                )
        ));

        OrganizationRepresentation existing = new OrganizationRepresentation();
        existing.setId("org-a-id");
        existing.setAlias("org-a");
        when(organizationRepository.search(eq("test"), eq("org-a"))).thenReturn(Optional.of(existing));

        OrganizationImport resolved = new OrganizationImport();
        resolved.setId("org-a-id");
        resolved.setAlias("org-a");
        when(organizationRepository.getByAlias(eq("test"), eq("org-a"))).thenReturn(resolved);

        IdentityProviderRepresentation idpKeep = new IdentityProviderRepresentation();
        idpKeep.setAlias("idp-keep");
        IdentityProviderRepresentation idpRemove = new IdentityProviderRepresentation();
        idpRemove.setAlias("idp-remove");
        when(organizationRepository.getIdentityProviders(eq("test"), eq("org-a-id"))).thenReturn(List.of(idpKeep, idpRemove));

        when(organizationRepository.getMembers(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());

        service.doImport(realmImport);

        verify(organizationRepository, times(1)).removeIdentityProvider(eq("test"), eq("org-a-id"), eq("idp-remove"));
        verify(organizationRepository, never()).removeIdentityProvider(eq("test"), eq("org-a-id"), eq("idp-keep"));
    }

    @Test
    void doImport_shouldNotUpdateWhenNoChangesDetected() {
        RealmImport realmImport = new RealmImport();
        realmImport.setRealm("test");
        realmImport.setOrganizationsRaw(List.of(Map.of("alias", "org-a")));

        OrganizationRepresentation existing = new OrganizationRepresentation();
        existing.setId("org-a-id");
        existing.setAlias("org-a");
        when(organizationRepository.search(eq("test"), eq("org-a"))).thenReturn(Optional.of(existing));

        OrganizationImport resolved = new OrganizationImport();
        resolved.setId("org-a-id");
        resolved.setAlias("org-a");
        when(organizationRepository.getByAlias(eq("test"), eq("org-a"))).thenReturn(resolved);

        when(organizationRepository.getIdentityProviders(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());
        when(organizationRepository.getMembers(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());

        service.doImport(realmImport);

        verify(organizationRepository, never()).update(eq("test"), any(OrganizationImport.class));
    }

    @Test
    void doImport_shouldNotAddMemberWhenAlreadyPresent() {
        RealmImport realmImport = new RealmImport();
        realmImport.setRealm("test");
        realmImport.setOrganizationsRaw(List.of(
                Map.of(
                        "alias", "org-a",
                        "members", List.of(Map.of("username", "alice"))
                )
        ));

        when(organizationRepository.getAll("test")).thenReturn(Collections.emptyList());
        when(organizationRepository.search(eq("test"), eq("org-a"))).thenReturn(Optional.empty());

        OrganizationImport created = new OrganizationImport();
        created.setAlias("org-a");
        created.setId("org-a-id");
        when(organizationRepository.getByAlias(eq("test"), eq("org-a"))).thenReturn(created);

        when(organizationRepository.getIdentityProviders(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());

        MemberRepresentation existingMember = new MemberRepresentation();
        existingMember.setUsername("alice");
        when(organizationRepository.getMembers(eq("test"), eq("org-a-id"))).thenReturn(List.of(existingMember));

        UserRepresentation alice = new UserRepresentation();
        alice.setId("alice-id");
        when(userRepository.search(eq("test"), eq("alice"))).thenReturn(Optional.of(alice));

        service.doImport(realmImport);

        verify(organizationRepository, never()).addMember(eq("test"), eq("org-a-id"), anyString());
    }

    @Test
    void doImport_fullManaged_shouldRemoveMembersMissingFromConfiguredList() {
        when(managedProperties.getOrganization()).thenReturn(ImportManagedPropertiesValues.FULL);

        RealmImport realmImport = new RealmImport();
        realmImport.setRealm("test");
        realmImport.setOrganizationsRaw(List.of(
                Map.of(
                        "alias", "org-a",
                        "members", List.of(Map.of("username", "alice"))
                )
        ));

        when(organizationRepository.getAll("test")).thenReturn(Collections.emptyList());
        when(organizationRepository.search(eq("test"), eq("org-a"))).thenReturn(Optional.empty());

        OrganizationImport created = new OrganizationImport();
        created.setAlias("org-a");
        created.setId("org-a-id");
        when(organizationRepository.getByAlias(eq("test"), eq("org-a"))).thenReturn(created);

        when(organizationRepository.getIdentityProviders(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());

        MemberRepresentation existingMember = new MemberRepresentation();
        existingMember.setUsername("bob");
        when(organizationRepository.getMembers(eq("test"), eq("org-a-id"))).thenReturn(List.of(existingMember));

        UserRepresentation bob = new UserRepresentation();
        bob.setId("bob-id");
        when(userRepository.search(eq("test"), eq("bob"))).thenReturn(Optional.of(bob));

        UserRepresentation alice = new UserRepresentation();
        alice.setId("alice-id");
        when(userRepository.search(eq("test"), eq("alice"))).thenReturn(Optional.of(alice));

        service.doImport(realmImport);

        verify(organizationRepository, times(1)).removeMember(eq("test"), eq("org-a-id"), eq("bob-id"));
    }

    @Test
    void doImport_shouldSkipMemberWhenUserNotFound() {
        RealmImport realmImport = new RealmImport();
        realmImport.setRealm("test");

        Map<String, Object> imported = new HashMap<>();
        imported.put("alias", "org-a");
        realmImport.setOrganizationsRaw(List.of(imported));

        when(organizationRepository.getAll("test")).thenReturn(Collections.emptyList());
        when(organizationRepository.search(eq("test"), eq("org-a"))).thenReturn(Optional.empty());

        OrganizationImport created = new OrganizationImport();
        created.setAlias("org-a");
        created.setId("org-a-id");
        when(organizationRepository.getByAlias(eq("test"), eq("org-a"))).thenReturn(created);

        when(organizationRepository.getIdentityProviders(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());
        when(organizationRepository.getMembers(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());

        realmImport.setOrganizationsRaw(List.of(
                Map.of("alias", "org-a", "members", List.of(Map.of("username", "missing-user")))
        ));

        when(userRepository.search(eq("test"), eq("missing-user"))).thenReturn(Optional.empty());

        service.doImport(realmImport);

        verify(organizationRepository, never()).addMember(eq("test"), eq("org-a-id"), anyString());
    }

    @Test
    void doImport_shouldKeepDomainRoutingWhenImportOmitsIt() {
        stubExistingOrganization(routedDomain("acme.com", "github", true));

        service.doImport(realmImportWith(Map.of(
                "alias", "org-a",
                "description", "changed",
                "domains", List.of(Map.of("name", "acme.com"))
        )));

        OrganizationDomainImport domain = (OrganizationDomainImport) captureUpdate().getDomain("acme.com");
        assertThat(domain.getIdentityProviderAlias(), is("github"));
        assertThat(domain.getAutoRedirect(), is(true));
    }

    @Test
    void doImport_shouldRemoveDomainRoutingOnEmptyAlias() {
        stubExistingOrganization(routedDomain("acme.com", "github", true));

        service.doImport(realmImportWith(Map.of(
                "alias", "org-a",
                "domains", List.of(Map.of("name", "acme.com", "identityProviderAlias", "", "autoRedirect", false))
        )));

        OrganizationDomainImport domain = (OrganizationDomainImport) captureUpdate().getDomain("acme.com");
        assertThat(domain.getIdentityProviderAlias(), is(nullValue()));
        assertThat(domain.getAutoRedirect(), is(false));
    }

    @Test
    void doImport_shouldNotUpdateWhenRoutingIsUnchanged() {
        stubExistingOrganization(routedDomain("acme.com", "github", true));

        service.doImport(realmImportWith(Map.of(
                "alias", "org-a",
                "domains", List.of(Map.of("name", "acme.com", "identityProviderAlias", "github"))
        )));

        verify(organizationRepository, never()).update(eq("test"), any(OrganizationImport.class));
    }

    @Test
    void doImport_shouldCreateWithoutRoutingAndLinkSettings() {
        when(organizationRepository.getAll("test")).thenReturn(Collections.emptyList());
        when(organizationRepository.search(eq("test"), eq("org-a"))).thenReturn(Optional.empty());
        OrganizationImport created = new OrganizationImport();
        created.setId("org-a-id");
        created.setAlias("org-a");
        when(organizationRepository.getByAlias(eq("test"), eq("org-a"))).thenReturn(created);
        when(organizationRepository.getIdentityProviders(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());
        when(organizationRepository.getMembers(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());

        service.doImport(realmImportWith(Map.of(
                "alias", "org-a",
                "domains", List.of(Map.of("name", "acme.com", "identityProviderAlias", "idp-a", "autoRedirect", true)),
                "identityProviders", List.of(Map.of("alias", "idp-a", "membershipType", "MANAGED"))
        )));

        ArgumentCaptor<OrganizationRepresentation> createCaptor = ArgumentCaptor.forClass(OrganizationRepresentation.class);
        verify(organizationRepository).create(eq("test"), createCaptor.capture());
        OrganizationRepresentation sent = createCaptor.getValue();
        assertThat(sent, not(instanceOf(OrganizationImport.class)));
        assertThat(sent.getDomains(), everyItem(not(instanceOf(OrganizationDomainImport.class))));
        assertThat(sent.getIdentityProviders(), is(nullValue()));

        InOrder order = inOrder(organizationRepository);
        order.verify(organizationRepository).addIdentityProvider("test", "org-a-id", "idp-a");
        order.verify(organizationRepository).update(eq("test"), any(OrganizationImport.class));
    }

    @Test
    void doImport_shouldUpdateIdentityProviderLinkSettings() {
        stubExistingOrganization();
        IdentityProviderRepresentation linked = new IdentityProviderRepresentation();
        linked.setAlias("idp-a");
        when(organizationRepository.getIdentityProviders(eq("test"), eq("org-a-id"))).thenReturn(List.of(linked));
        when(organizationRepository.getIdentityProviderLink("test", "org-a-id", "idp-a"))
                .thenReturn(new OrganizationIdentityProviderLinkRepresentation(true, "UNMANAGED"));

        service.doImport(realmImportWith(Map.of(
                "alias", "org-a",
                "identityProviders", List.of(Map.of("alias", "idp-a", "membershipType", "MANAGED"))
        )));

        ArgumentCaptor<OrganizationIdentityProviderLinkRepresentation> linkCaptor =
                ArgumentCaptor.forClass(OrganizationIdentityProviderLinkRepresentation.class);
        verify(organizationRepository).updateIdentityProviderLink(eq("test"), eq("org-a-id"), eq("idp-a"), linkCaptor.capture());
        assertThat(linkCaptor.getValue().getAutoMembership(), is(true));
        assertThat(linkCaptor.getValue().getMembershipType(), is("MANAGED"));
        verify(organizationRepository, never()).addIdentityProvider(anyString(), anyString(), anyString());
    }

    @Test
    void doImport_shouldSkipIdentityProviderLinkWhenSettingsMatch() {
        stubExistingOrganization();
        IdentityProviderRepresentation linked = new IdentityProviderRepresentation();
        linked.setAlias("idp-a");
        when(organizationRepository.getIdentityProviders(eq("test"), eq("org-a-id"))).thenReturn(List.of(linked));
        when(organizationRepository.getIdentityProviderLink("test", "org-a-id", "idp-a"))
                .thenReturn(new OrganizationIdentityProviderLinkRepresentation(true, "MANAGED"));

        service.doImport(realmImportWith(Map.of(
                "alias", "org-a",
                "identityProviders", List.of(Map.of("alias", "idp-a", "membershipType", "MANAGED"))
        )));

        verify(organizationRepository, never()).updateIdentityProviderLink(anyString(), anyString(), anyString(), any());
    }

    private RealmImport realmImportWith(Map<String, Object> organization) {
        RealmImport realmImport = new RealmImport();
        realmImport.setRealm("test");
        realmImport.setOrganizationsRaw(List.of(new HashMap<>(organization)));
        return realmImport;
    }

    private void stubExistingOrganization(OrganizationDomainRepresentation... domains) {
        OrganizationImport existing = new OrganizationImport();
        existing.setId("org-a-id");
        existing.setAlias("org-a");
        for (OrganizationDomainRepresentation domain : domains) {
            existing.addDomain(domain);
        }
        when(organizationRepository.search(eq("test"), eq("org-a"))).thenReturn(Optional.of(existing));
        when(organizationRepository.getByAlias(eq("test"), eq("org-a"))).thenReturn(existing);
        when(organizationRepository.getIdentityProviders(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());
        when(organizationRepository.getMembers(eq("test"), eq("org-a-id"))).thenReturn(Collections.emptyList());
    }

    private static OrganizationDomainImport routedDomain(String name, String idpAlias, boolean autoRedirect) {
        OrganizationDomainImport domain = new OrganizationDomainImport();
        domain.setName(name);
        domain.setIdentityProviderAlias(idpAlias);
        domain.setAutoRedirect(autoRedirect);
        return domain;
    }

    private OrganizationImport captureUpdate() {
        ArgumentCaptor<OrganizationImport> captor = ArgumentCaptor.forClass(OrganizationImport.class);
        verify(organizationRepository).update(eq("test"), captor.capture());
        return captor.getValue();
    }
}
