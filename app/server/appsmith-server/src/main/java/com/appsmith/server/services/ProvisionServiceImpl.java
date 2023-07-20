package com.appsmith.server.services;

import com.appsmith.external.models.Policy;
import com.appsmith.server.acl.PolicyGenerator;
import com.appsmith.server.constants.FieldName;
import com.appsmith.server.domains.Config;
import com.appsmith.server.domains.PermissionGroup;
import com.appsmith.server.domains.QUser;
import com.appsmith.server.domains.QUserGroup;
import com.appsmith.server.domains.Tenant;
import com.appsmith.server.domains.User;
import com.appsmith.server.domains.UserGroup;
import com.appsmith.server.dtos.ApiKeyRequestDto;
import com.appsmith.server.dtos.DisconnectProvisioningDto;
import com.appsmith.server.dtos.ProvisionStatusDTO;
import com.appsmith.server.exceptions.AppsmithError;
import com.appsmith.server.exceptions.AppsmithException;
import com.appsmith.server.helpers.ProvisionUtils;
import com.appsmith.server.helpers.TenantUtils;
import com.appsmith.server.helpers.UserUtils;
import com.appsmith.server.repositories.UserGroupRepository;
import com.appsmith.server.repositories.UserRepository;
import com.appsmith.server.solutions.UserAndAccessManagementService;
import lombok.AllArgsConstructor;
import net.minidev.json.JSONObject;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.appsmith.server.acl.AclPermission.DELETE_USERS;
import static com.appsmith.server.acl.AclPermission.MANAGE_TENANT;
import static com.appsmith.server.acl.AclPermission.MANAGE_USERS;
import static com.appsmith.server.acl.AclPermission.RESET_PASSWORD_USERS;
import static com.appsmith.server.constants.FieldName.CONFIGURED_STATUS;
import static com.appsmith.server.constants.FieldName.PROVISIONING_LAST_UPDATED_AT;
import static com.appsmith.server.constants.FieldName.PROVISIONING_STATUS;
import static com.appsmith.server.enums.ProvisionStatus.ACTIVE;
import static com.appsmith.server.enums.ProvisionStatus.INACTIVE;
import static com.appsmith.server.repositories.ce.BaseAppsmithRepositoryCEImpl.fieldName;

@Component
@AllArgsConstructor
public class ProvisionServiceImpl implements ProvisionService {
    private final ApiKeyService apiKeyService;
    private final TenantUtils tenantUtils;
    private final TenantService tenantService;
    private final UserRepository userRepository;
    private final UserAndAccessManagementService userAndAccessManagementService;
    private final UserGroupRepository userGroupRepository;
    private final PolicyGenerator policyGenerator;
    private final UserUtils userUtils;
    private final ProvisionUtils provisionUtils;

    @Override
    public Mono<String> generateProvisionToken() {
        ApiKeyRequestDto apiKeyRequestDto =
                ApiKeyRequestDto.builder().email(FieldName.PROVISIONING_USER).build();

        return tenantUtils
                .enterpriseUpgradeRequired()
                .then(apiKeyService.generateApiKey(apiKeyRequestDto))
                .flatMap(apiKey -> provisionUtils.updateConfiguredStatus(true).thenReturn(apiKey));
    }

    @Override
    public Mono<ProvisionStatusDTO> getProvisionStatus() {
        // Check if the User has manage tenant permissions
        Mono<Tenant> tenantMono = tenantService
                .getDefaultTenant(MANAGE_TENANT)
                .switchIfEmpty(Mono.error(
                        new AppsmithException(AppsmithError.ACTION_IS_NOT_AUTHORIZED, "get provisioning status")))
                .cache();

        // Get Provision Status config
        Mono<Config> provisioningStatusConfigMono = provisionUtils.getOrCreateProvisioningStatusConfig();

        return provisioningStatusConfigMono.flatMap(provisioningStatusConfig -> {
            JSONObject config = provisioningStatusConfig.getConfig();
            // If inactive, just return ProvisionStatusDTO with status as Inactive
            if (INACTIVE.getValue().equals(config.get(PROVISIONING_STATUS))) {
                return Mono.just(ProvisionStatusDTO.builder()
                        .provisionStatus(INACTIVE.getValue())
                        .configuredStatus((Boolean) config.get(CONFIGURED_STATUS))
                        .build());
            }
            // If active, set the last updated as
            // Get count of all provisioned users from Repo without permission
            // Get count of all provisioned groups from Repo without permission
            String lastUpdatedAt = (String) config.get(PROVISIONING_LAST_UPDATED_AT);
            Mono<Long> provisionedUsersCountMono =
                    userRepository.countAllUsersByIsProvisioned(Boolean.TRUE, Optional.empty());
            Mono<Long> provisionedUserGroupsCountMono =
                    userGroupRepository.countAllUserGroupsByIsProvisioned(Boolean.TRUE, Optional.empty());

            return tenantMono.flatMap(tenant -> Mono.zip(provisionedUsersCountMono, provisionedUserGroupsCountMono)
                    .map(pair -> {
                        Long countProvisionedUsers = pair.getT1();
                        Long countProvisionedUserGroups = pair.getT2();
                        return ProvisionStatusDTO.builder()
                                .provisionStatus(ACTIVE.getValue())
                                .lastUpdatedAt(lastUpdatedAt)
                                .provisionedUsers(countProvisionedUsers)
                                .provisionedGroups(countProvisionedUserGroups)
                                .configuredStatus((Boolean) config.get(CONFIGURED_STATUS))
                                .build();
                    }));
        });
    }

    public Mono<Boolean> archiveProvisionToken() {
        ApiKeyRequestDto apiKeyRequestDto =
                ApiKeyRequestDto.builder().email(FieldName.PROVISIONING_USER).build();
        return apiKeyService.archiveApiKey(apiKeyRequestDto);
    }

    @Override
    public Mono<Boolean> disconnectProvisioning(DisconnectProvisioningDto disconnectProvisioningDto) {
        Mono<Boolean> deleteOrUpdateUsersGroupsAndUpdateAssociatedRolesMono;

        // Check whether user is authorised to MANAGE_TENANT
        // Throw AppsmithError ACTION_IS_NOT_AUTHORIZED
        Mono<Tenant> tenantMono = tenantService
                .getDefaultTenant(MANAGE_TENANT)
                .switchIfEmpty(Mono.error(
                        new AppsmithException(AppsmithError.ACTION_IS_NOT_AUTHORIZED, "disconnect provisioning")))
                .cache();

        if (disconnectProvisioningDto.isKeepAllProvisionedResources()) {
            deleteOrUpdateUsersGroupsAndUpdateAssociatedRolesMono =
                    transferManagementPoliciesToInstanceAdministratorForAllProvisionedUsersAndGroups(tenantMono);
        } else {
            deleteOrUpdateUsersGroupsAndUpdateAssociatedRolesMono =
                    deleteAllProvisionedUsersAndGroupsAndUpdateAllAssociatedRoles();
        }

        // Delete provisioned users & Groups and update Associated roles or
        // update access policies for all provisioned users and groups
        // then archive provision token
        return tenantMono.flatMap(tenant -> deleteOrUpdateUsersGroupsAndUpdateAssociatedRolesMono
                .flatMap(usersGroupsRolesUpdated -> archiveProvisionToken())
                .flatMap(archiveProvisionToken -> provisionUtils.updateStatus(INACTIVE, false)));
    }

    @NotNull private Mono<Boolean> transferManagementPoliciesToInstanceAdministratorForAllProvisionedUsersAndGroups(
            Mono<Tenant> tenantMono) {
        return tenantMono.flatMap(tenant -> {
            // Update permissions for User resources
            // Instance admin role alone should have delete user permission. (Get Role Id from Instance Admin Role)
            // User Management role alone should have manage user permission. (Get Role Id from role id present in RESET
            // PASSWORD USER policy.)
            // Keep the read user permission as is.
            Mono<Boolean> updatedUsersMono =
                    updateAllProvisionedUsersDeleteAndManagePolicyWithSuperAdminAndUserManagementRole();

            // Update the permissions for the User group resources
            // Inherit all the permissions from tenant to user group.
            Mono<Boolean> updateUserGroupsPoliciesMono = updatedAllProvisionedGroupsWithInheritedTenantPolicies(tenant);
            return Mono.zip(updatedUsersMono, updateUserGroupsPoliciesMono).map(pair -> Boolean.TRUE);
        });
    }

    @NotNull private Mono<Boolean> deleteAllProvisionedUsersAndGroupsAndUpdateAllAssociatedRoles() {
        // We are interested only in the policies and email of the provisioned User resources.
        // We are interested only in the policies and users of the provisioned UserGroup resources.
        List<String> includeFieldsUsers = List.of(fieldName(QUser.user.policies), fieldName(QUser.user.email));
        List<String> includeFieldsGroups =
                List.of(fieldName(QUser.user.policies), fieldName(QUserGroup.userGroup.users));
        // find all User with isProvisioned == true
        Mono<List<User>> provisionedUsersMono = userRepository
                .getAllUsersByIsProvisioned(Boolean.TRUE, Optional.of(includeFieldsUsers), Optional.empty())
                .collectList()
                .cache();
        // find all User Groups with isProvisioned == true
        Mono<List<UserGroup>> provisionedGroupsMono = userGroupRepository
                .getAllUserGroupsByIsProvisioned(Boolean.TRUE, Optional.of(includeFieldsGroups), Optional.empty())
                .collectList()
                .cache();

        // Un-assign all these users from all the roles that they are associated with.
        // Un-assign all these usergroups from all the roles that they are associated with.
        Mono<Boolean> unassignProvisionedEntitiesFromAllAssociatedRolesMono = Mono.zip(
                        provisionedUsersMono, provisionedGroupsMono)
                .flatMap(pair -> {
                    List<User> provisionedUsers = pair.getT1();
                    List<UserGroup> provisionedGroups = pair.getT2();
                    return userAndAccessManagementService.unAssignUsersAndGroupsFromAllAssociatedRoles(
                            provisionedUsers, provisionedGroups);
                });

        // delete users.
        // delete user groups.
        Mono<Boolean> deleteAllProvisionedEntitiesMono =
                deleteUsersAndGroups(provisionedUsersMono, provisionedGroupsMono);

        // Un-assign then delete
        return unassignProvisionedEntitiesFromAllAssociatedRolesMono.flatMap(
                unassignedFromRoles -> deleteAllProvisionedEntitiesMono);
    }

    @NotNull private Mono<Boolean> deleteUsersAndGroups(
            Mono<List<User>> provisionedUsersMono, Mono<List<UserGroup>> provisionedGroupsMono) {
        return Mono.zip(provisionedUsersMono, provisionedGroupsMono).flatMap(pair -> {
            List<User> provisionedUsers = pair.getT1();
            List<UserGroup> provisionedGroups = pair.getT2();
            List<String> provisionedUserIds =
                    provisionedUsers.stream().map(User::getId).toList();
            List<String> provisionedGroupIds =
                    provisionedGroups.stream().map(UserGroup::getId).toList();
            Mono<Boolean> deleteProvisionedUsersByIdMono =
                    userRepository.deleteAllById(provisionedUserIds).thenReturn(Boolean.TRUE);
            Mono<Boolean> deleteProvisionedGroupsByIdMono =
                    userGroupRepository.deleteAllById(provisionedGroupIds).thenReturn(Boolean.TRUE);
            return Mono.zip(deleteProvisionedUsersByIdMono, deleteProvisionedGroupsByIdMono)
                    .map(pair1 -> Boolean.TRUE);
        });
    }

    private Mono<Boolean> updateAllProvisionedUsersDeleteAndManagePolicyWithSuperAdminAndUserManagementRole() {
        List<String> includeFieldsUsers = List.of(fieldName(QUser.user.policies));
        Flux<User> provisionedUserFlux = userRepository
                .getAllUsersByIsProvisioned(Boolean.TRUE, Optional.of(includeFieldsUsers), Optional.empty())
                .cache();
        return userUtils
                .getSuperAdminPermissionGroup()
                .flatMap(superAdminRole -> provisionedUserFlux
                        .flatMap(user -> {
                            Set<Policy> updatedUserPolicies =
                                    getUserPoliciesWithUpdatedDeleteAndManageUserPolicies(user, superAdminRole);
                            return userRepository.updateUserPoliciesAndIsProvisionedWithoutPermission(
                                    user.getId(), Boolean.FALSE, updatedUserPolicies);
                        })
                        .collectList())
                .map(list -> Boolean.TRUE);
    }

    private Mono<Boolean> updatedAllProvisionedGroupsWithInheritedTenantPolicies(Tenant tenant) {
        Set<Policy> policies = policyGenerator.getAllChildPolicies(tenant.getPolicies(), Tenant.class, UserGroup.class);
        return userGroupRepository.updateProvisionedUserGroupsPoliciesAndIsProvisionedWithoutPermission(
                Boolean.FALSE, policies);
    }

    private Set<Policy> getUserPoliciesWithUpdatedDeleteAndManageUserPolicies(
            User user, PermissionGroup instanceAdminRole) {
        Set<Policy> policiesWithoutDeleteAndManagePermissions = user.getPolicies().stream()
                .filter(policy -> !policy.getPermission().equals(MANAGE_USERS.getValue())
                        && !policy.getPermission().equals(DELETE_USERS.getValue()))
                .collect(Collectors.toSet());
        Policy resetPasswordPolicy = policiesWithoutDeleteAndManagePermissions.stream()
                .filter(policy -> RESET_PASSWORD_USERS.getValue().equals(policy.getPermission()))
                .findFirst()
                .get();
        Policy deleteUserPolicy = Policy.builder()
                .permission(DELETE_USERS.getValue())
                .permissionGroups(Set.of(instanceAdminRole.getId()))
                .build();
        Policy manageUserPolicy = Policy.builder()
                .permission(MANAGE_USERS.getValue())
                .permissionGroups(resetPasswordPolicy.getPermissionGroups())
                .build();

        Set<Policy> newUserPolicies = new HashSet<>();
        newUserPolicies.addAll(policiesWithoutDeleteAndManagePermissions);
        newUserPolicies.add(deleteUserPolicy);
        newUserPolicies.add(manageUserPolicy);
        return newUserPolicies;
    }
}
