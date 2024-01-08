package com.appsmith.server.services;

import com.appsmith.external.exceptions.ErrorDTO;
import com.appsmith.external.models.ActionDTO;
import com.appsmith.external.models.CreatorContextType;
import com.appsmith.external.models.Datasource;
import com.appsmith.server.actioncollections.base.ActionCollectionService;
import com.appsmith.server.applications.base.ApplicationService;
import com.appsmith.server.datasources.base.DatasourceService;
import com.appsmith.server.domains.ApplicationMode;
import com.appsmith.server.domains.CustomJSLib;
import com.appsmith.server.domains.Plugin;
import com.appsmith.server.domains.Tenant;
import com.appsmith.server.domains.Theme;
import com.appsmith.server.domains.User;
import com.appsmith.server.dtos.ActionCollectionDTO;
import com.appsmith.server.dtos.ActionCollectionViewDTO;
import com.appsmith.server.dtos.ActionViewDTO;
import com.appsmith.server.dtos.ApplicationPagesDTO;
import com.appsmith.server.dtos.ConsolidatedAPIResponseDTO;
import com.appsmith.server.dtos.MockDataDTO;
import com.appsmith.server.dtos.MockDataSet;
import com.appsmith.server.dtos.PageDTO;
import com.appsmith.server.dtos.ProductAlertResponseDTO;
import com.appsmith.server.dtos.ResponseDTO;
import com.appsmith.server.dtos.UserProfileDTO;
import com.appsmith.server.exceptions.AppsmithError;
import com.appsmith.server.exceptions.AppsmithException;
import com.appsmith.server.jslibs.base.CustomJSLibService;
import com.appsmith.server.newactions.base.NewActionService;
import com.appsmith.server.newpages.base.NewPageService;
import com.appsmith.server.plugins.base.PluginService;
import com.appsmith.server.themes.base.ThemeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.appsmith.external.constants.PluginConstants.PackageName.GRAPHQL_PLUGIN;
import static com.appsmith.external.constants.PluginConstants.PackageName.REST_API_PLUGIN;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.CURRENT_THEME;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.DATASOURCES;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.FEATURE_FLAG;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.IS_API_ACCESSIBLE_TO_ANONYMOUS_USER_MAP;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.MOCK_DATASOURCES;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.PAGES;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.PLUGINS;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.PLUGIN_FORM_CONFIGS;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.PRODUCT_ALERT;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.PUBLISHED_ACTIONS;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.PUBLISHED_ACTION_COLLECTIONS;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.PUBLISHED_JS_LIBS;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.PUBLISHED_PAGE_WITH_MIGRATED_DSL;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.TENANTS;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.THEMES;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.UNPUBLISHED_ACTIONS;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.UNPUBLISHED_ACTION_COLLECTIONS;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.UNPUBLISHED_JS_LIBS;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.UNPUBLISHED_PAGE_WITH_MIGRATED_DSL;
import static com.appsmith.server.constants.ConsolidatedApiAccessibilityMap.USER_PROFILE;
import static com.appsmith.server.constants.ce.FieldNameCE.APPLICATION_ID;
import static com.appsmith.server.constants.ce.FieldNameCE.APP_MODE;
import static com.appsmith.server.constants.ce.FieldNameCE.WORKSPACE_ID;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
@Service
public class ConsolidatedAPIServiceImpl implements ConsolidatedAPIService {
    private static final String FEATURE_FLAG_RELEASE_SERVER_DSL_MIGRATIONS_ENABLED =
            "release_server_dsl_migrations_enabled";
    public static final int INTERNAL_SERVER_ERROR_STATUS = AppsmithError.INTERNAL_SERVER_ERROR.getHttpErrorCode();
    public static final String INTERNAL_SERVER_ERROR_CODE = AppsmithError.INTERNAL_SERVER_ERROR.getAppErrorCode();
    public static final String EMPTY_WORKSPACE_ID_ON_ERROR = "";

    private final SessionUserService sessionUserService;
    private final UserService userService;
    private final UserDataService userDataService;
    private final TenantService tenantService;
    private final ProductAlertService productAlertService;
    private final NewPageService newPageService;
    private final NewActionService newActionService;
    private final ActionCollectionService actionCollectionService;
    private final ThemeService themeService;
    private final ApplicationPageService applicationPageService;
    private final CustomJSLibService customJSLibService;
    private final PluginService pluginService;
    private final ApplicationService applicationService;
    private final DatasourceService datasourceService;
    private final MockDataService mockDataService;

    public ConsolidatedAPIServiceImpl(
            SessionUserService sessionUserService,
            UserService userService,
            UserDataService userDataService,
            TenantService tenantService,
            ProductAlertService productAlertService,
            NewPageService newPageService,
            NewActionService newActionService,
            ActionCollectionService actionCollectionService,
            ThemeService themeService,
            ApplicationPageService applicationPageService,
            CustomJSLibService customJSLibService,
            PluginService pluginService,
            ApplicationService applicationService,
            DatasourceService datasourceService,
            MockDataService mockDataService) {
        this.sessionUserService = sessionUserService;
        this.userService = userService;
        this.userDataService = userDataService;
        this.tenantService = tenantService;
        this.productAlertService = productAlertService;
        this.newPageService = newPageService;
        this.newActionService = newActionService;
        this.actionCollectionService = actionCollectionService;
        this.themeService = themeService;
        this.applicationPageService = applicationPageService;
        this.customJSLibService = customJSLibService;
        this.pluginService = pluginService;
        this.applicationService = applicationService;
        this.datasourceService = datasourceService;
        this.mockDataService = mockDataService;
    }

    <T> ResponseDTO<T> getSuccessResponse(T data) {
        return new ResponseDTO<>(HttpStatus.OK.value(), data, null);
    }

    <T> Mono<ResponseDTO<T>> getErrorResponseMono(Throwable error, Class<T> type) {
        if (error instanceof AppsmithException appsmithException) {
            return Mono.just(new ResponseDTO<T>(
                    appsmithException.getHttpStatus(),
                    new ErrorDTO(
                            appsmithException.getAppErrorCode(),
                            appsmithException.getErrorType(),
                            appsmithException.getMessage(),
                            appsmithException.getTitle())));
        }

        return Mono.just(new ResponseDTO<T>(
                INTERNAL_SERVER_ERROR_STATUS, new ErrorDTO(INTERNAL_SERVER_ERROR_CODE, error.getMessage())));
    }

    /**
     * This method checks if a given sub API is accessible to the anonymous user or not. If not, then it returns 401
     * error. Whether a sub API is accessible or not is given by the map IS_API_ACCESSIBLE_TO_ANONYMOUS_USER_MAP .
     */
    Mono<Boolean> checkApiAccessIfAnonymousUser(Mono<User> userMono, String api) {
        return userMono.flatMap(user -> {
            if (!user.getIsAnonymous() || IS_API_ACCESSIBLE_TO_ANONYMOUS_USER_MAP.get(api)) {
                return Mono.just(true);
            }

            return Mono.error(new AppsmithException(AppsmithError.USER_NOT_SIGNED_IN));
        });
    }

    /**
     * This method is meant to be used by the client application at the time of 1st page load. Client currently makes
     * several API calls to fetch all the required data. This method consolidates all that data and returns them as
     * response hence enabling the client to fetch the required data via a single API call only.
     *
     * PLEASE TAKE CARE TO USE .cache() FOR Mono THAT GETS REUSED SO THAT FIRST PAGE LOAD PERFORMANCE DOES NOT DEGRADE.
     */
    @Override
    public Mono<ConsolidatedAPIResponseDTO> getConsolidatedInfoForPageLoad(
            String defaultPageId, String applicationId, String branchName, ApplicationMode mode) {

        /* if either of pageId or applicationId are provided then application mode must also be provided */
        if (mode == null && (!isBlank(defaultPageId) || !isBlank(applicationId))) {
            return Mono.error(new AppsmithException(AppsmithError.INVALID_PARAMETER, APP_MODE));
        }

        /* This object will serve as a container to hold the response of this method*/
        ConsolidatedAPIResponseDTO consolidatedAPIResponseDTO = new ConsolidatedAPIResponseDTO();

        Mono<User> userMonoCache = sessionUserService.getCurrentUser().cache();

        /* Get user profile data */
        Mono<ResponseDTO<UserProfileDTO>> userProfileDTOResponseDTOMono = checkApiAccessIfAnonymousUser(
                        userMonoCache, USER_PROFILE)
                .flatMap(ignore ->
                        userMonoCache.flatMap(userService::buildUserProfileDTO).map(this::getSuccessResponse))
                .onErrorResume(error -> getErrorResponseMono(error, UserProfileDTO.class));

        /* Get all feature flags data */
        Mono<ResponseDTO<Map>> featureFlagsForCurrentUserResponseDTOMonoCache = checkApiAccessIfAnonymousUser(
                        userMonoCache, FEATURE_FLAG)
                .flatMap(ignore -> userDataService
                        .getFeatureFlagsForCurrentUser()
                        .map(res -> (Map) res)
                        .map(this::getSuccessResponse))
                .onErrorResume(error -> getErrorResponseMono(error, Map.class))
                .cache();

        /* Get tenant config data */
        Mono<ResponseDTO<Tenant>> tenantResponseDTOMono = checkApiAccessIfAnonymousUser(userMonoCache, TENANTS)
                .flatMap(ignore -> tenantService.getTenantConfiguration().map(this::getSuccessResponse))
                .onErrorResume(error -> getErrorResponseMono(error, Tenant.class));

        /* Get any product alert info */
        Mono<ResponseDTO<ProductAlertResponseDTO>> productAlertResponseDTOMono = checkApiAccessIfAnonymousUser(
                        userMonoCache, PRODUCT_ALERT)
                .flatMap(ignore -> productAlertService
                        .getSingleApplicableMessage()
                        .map(messages -> {
                            if (!messages.isEmpty()) {
                                return messages.get(0);
                            }

                            return new ProductAlertResponseDTO();
                        })
                        .map(this::getSuccessResponse))
                .onErrorResume(error -> getErrorResponseMono(error, ProductAlertResponseDTO.class));

        if (isBlank(defaultPageId) && isBlank(applicationId)) {

            List<Mono<?>> listOfCommonResponseMono = List.of(
                    userProfileDTOResponseDTOMono,
                    featureFlagsForCurrentUserResponseDTOMonoCache,
                    tenantResponseDTOMono,
                    productAlertResponseDTOMono);

            return Mono.zip(listOfCommonResponseMono, responseArray -> {
                consolidatedAPIResponseDTO.setUserProfile((ResponseDTO<UserProfileDTO>) responseArray[0]);
                consolidatedAPIResponseDTO.setFeatureFlags((ResponseDTO<Map<String, Boolean>>) responseArray[1]);
                consolidatedAPIResponseDTO.setTenantConfig((ResponseDTO<Tenant>) responseArray[2]);
                consolidatedAPIResponseDTO.setProductAlert((ResponseDTO<ProductAlertResponseDTO>) responseArray[3]);

                return consolidatedAPIResponseDTO;
            });
        }

        /* Get view mode - EDIT or PUBLISHED */
        boolean isViewMode = ApplicationMode.PUBLISHED.equals(mode);

        /* Fetch application id if not provided */
        Mono<String> applicationIdMonoCache;
        if (isBlank(applicationId)) {
            applicationIdMonoCache = newPageService
                    .findRootApplicationIdFromNewPage(branchName, defaultPageId)
                    .cache();
        } else {
            applicationIdMonoCache = Mono.just(applicationId).cache();
        }

        /* Get all pages in application */
        Mono<ResponseDTO<ApplicationPagesDTO>> applicationPagesDTOResponseDTOMonoCache = checkApiAccessIfAnonymousUser(
                        userMonoCache, PAGES)
                .flatMap(ignore -> applicationIdMonoCache
                        .flatMap(appId -> newPageService.findApplicationPages(appId, null, branchName, mode))
                        .map(this::getSuccessResponse))
                .onErrorResume(error -> getErrorResponseMono(error, ApplicationPagesDTO.class))
                .cache();

        /* Get current theme */
        Mono<ResponseDTO<Theme>> applicationThemeResponseDTOMono = checkApiAccessIfAnonymousUser(
                        userMonoCache, CURRENT_THEME)
                .flatMap(ignore -> applicationIdMonoCache
                        .flatMap(appId -> themeService.getApplicationTheme(appId, mode, branchName))
                        .map(this::getSuccessResponse))
                .onErrorResume(error -> getErrorResponseMono(error, Theme.class));

        /* Get all themes */
        Mono<ResponseDTO<List>> ThemesListResponseDTOMono = checkApiAccessIfAnonymousUser(userMonoCache, THEMES)
                .flatMap(ignore -> applicationIdMonoCache
                        .flatMap(appId -> themeService
                                .getApplicationThemes(appId, branchName)
                                .collectList())
                        .map(res -> (List) res)
                        .map(this::getSuccessResponse))
                .onErrorResume(error -> getErrorResponseMono(error, List.class));

        /* Get all custom JS libraries installed in the application */
        Mono<ResponseDTO<List>> allJSLibsInContextDTOResponseDTOMono = checkApiAccessIfAnonymousUser(
                        userMonoCache, isViewMode ? PUBLISHED_JS_LIBS : UNPUBLISHED_JS_LIBS)
                .flatMap(ignore -> applicationIdMonoCache
                        .flatMap(appId -> customJSLibService.getAllJSLibsInContext(
                                appId, CreatorContextType.APPLICATION, branchName, isViewMode))
                        .map(res -> (List) res)
                        .map(this::getSuccessResponse))
                .onErrorResume(error -> getErrorResponseMono(error, List.class));

        /* Check if release_server_dsl_migrations_enabled flag is true for the user */
        Mono<Boolean> migrateDslMonoCache = featureFlagsForCurrentUserResponseDTOMonoCache
                .map(responseDTO -> {
                    if (HttpStatus.OK.value() != responseDTO.getResponseMeta().getStatus()) {
                        return Map.of();
                    }

                    return responseDTO.getData();
                })
                .map(flagsMap -> {
                    if (!flagsMap.containsKey(FEATURE_FLAG_RELEASE_SERVER_DSL_MIGRATIONS_ENABLED)) {
                        return false;
                    }

                    return (Boolean) flagsMap.get(FEATURE_FLAG_RELEASE_SERVER_DSL_MIGRATIONS_ENABLED);
                })
                .cache();

        Mono<ResponseDTO<PageDTO>> currentPageDTOResponseDTOMono = Mono.empty();
        if (!isBlank(defaultPageId)) {
            /* Get current page */
            currentPageDTOResponseDTOMono = checkApiAccessIfAnonymousUser(
                            userMonoCache,
                            isViewMode ? PUBLISHED_PAGE_WITH_MIGRATED_DSL : UNPUBLISHED_PAGE_WITH_MIGRATED_DSL)
                    .flatMap(ignore -> migrateDslMonoCache
                            .flatMap(migrateDsl -> applicationPageService.getPageAndMigrateDslByBranchAndDefaultPageId(
                                    defaultPageId, branchName, isViewMode, migrateDsl))
                            .map(this::getSuccessResponse))
                    .onErrorResume(error -> getErrorResponseMono(error, PageDTO.class));
        }

        /* Fetch view specific data */
        if (isViewMode) {
            /* Get list of all actions in view mode */
            Mono<ResponseDTO<List>> listOfActionViewResponseDTOMono = checkApiAccessIfAnonymousUser(
                            userMonoCache, PUBLISHED_ACTIONS)
                    .flatMap(ignore -> applicationIdMonoCache
                            .flatMap(appId -> newActionService
                                    .getActionsForViewMode(appId, branchName)
                                    .collectList())
                            .map(res -> (List) res)
                            .map(this::getSuccessResponse))
                    .onErrorResume(error -> getErrorResponseMono(error, List.class));

            /* Get list of all action collections in view mode */
            Mono<ResponseDTO<List>> listOfActionCollectionViewResponseDTOMono = checkApiAccessIfAnonymousUser(
                            userMonoCache, PUBLISHED_ACTION_COLLECTIONS)
                    .flatMap(ignore -> applicationIdMonoCache
                            .flatMap(appId -> actionCollectionService
                                    .getActionCollectionsForViewMode(appId, branchName)
                                    .collectList())
                            .map(res -> (List) res)
                            .map(this::getSuccessResponse))
                    .onErrorResume(error -> getErrorResponseMono(error, List.class));

            /* This list contains the Mono objects corresponding to all the data points required for view mode. All
             * the Mono objects in this list will be evaluated via Mono.zip operator.
             */
            List<Mono<?>> listOfMonoForPublishedApp = new ArrayList<>(List.of(
                    userProfileDTOResponseDTOMono,
                    tenantResponseDTOMono,
                    featureFlagsForCurrentUserResponseDTOMonoCache,
                    applicationPagesDTOResponseDTOMonoCache,
                    applicationThemeResponseDTOMono,
                    ThemesListResponseDTOMono,
                    listOfActionViewResponseDTOMono,
                    listOfActionCollectionViewResponseDTOMono,
                    allJSLibsInContextDTOResponseDTOMono,
                    productAlertResponseDTOMono));

            if (!isBlank(defaultPageId)) {
                listOfMonoForPublishedApp.add(currentPageDTOResponseDTOMono);
            }

            return Mono.zip(listOfMonoForPublishedApp, responseArray -> {
                consolidatedAPIResponseDTO.setUserProfile((ResponseDTO<UserProfileDTO>) responseArray[0]);
                consolidatedAPIResponseDTO.setTenantConfig((ResponseDTO<Tenant>) responseArray[1]);
                consolidatedAPIResponseDTO.setFeatureFlags((ResponseDTO<Map<String, Boolean>>) responseArray[2]);
                consolidatedAPIResponseDTO.setPages((ResponseDTO<ApplicationPagesDTO>) responseArray[3]);
                consolidatedAPIResponseDTO.setCurrentTheme((ResponseDTO<Theme>) responseArray[4]);
                consolidatedAPIResponseDTO.setThemes((ResponseDTO<List<Theme>>) responseArray[5]);
                consolidatedAPIResponseDTO.setPublishedActions((ResponseDTO<List<ActionViewDTO>>) responseArray[6]);
                consolidatedAPIResponseDTO.setPublishedActionCollections(
                        (ResponseDTO<List<ActionCollectionViewDTO>>) responseArray[7]);
                consolidatedAPIResponseDTO.setCustomJSLibraries((ResponseDTO<List<CustomJSLib>>) responseArray[8]);
                consolidatedAPIResponseDTO.setProductAlert((ResponseDTO<ProductAlertResponseDTO>) responseArray[9]);

                if (!isBlank(defaultPageId)) {
                    consolidatedAPIResponseDTO.setPageWithMigratedDsl((ResponseDTO<PageDTO>) responseArray[10]);
                }

                return consolidatedAPIResponseDTO;
            });
        } else {
            /* Get all actions in edit mode */
            Mono<ResponseDTO<List>> listOfActionResponseDTOMono = checkApiAccessIfAnonymousUser(
                            userMonoCache, UNPUBLISHED_ACTIONS)
                    .flatMap(ignore -> applicationIdMonoCache
                            .flatMap(appId -> {
                                MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                                params.add(APPLICATION_ID, appId);
                                return newActionService
                                        .getUnpublishedActions(params, branchName, false)
                                        .collectList();
                            })
                            .map(res -> (List) res)
                            .map(this::getSuccessResponse))
                    .onErrorResume(error -> getErrorResponseMono(error, List.class));

            /* Get all action collections in edit mode */
            Mono<ResponseDTO<List>> listOfActionCollectionResponseDTOMono = checkApiAccessIfAnonymousUser(
                            userMonoCache, UNPUBLISHED_ACTION_COLLECTIONS)
                    .flatMap(ignore -> applicationIdMonoCache.flatMap(appId -> {
                        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                        params.add(APPLICATION_ID, appId);
                        return actionCollectionService
                                .getPopulatedActionCollectionsByViewMode(params, false, branchName)
                                .collectList()
                                .map(res -> (List) res)
                                .map(this::getSuccessResponse);
                    }))
                    .onErrorResume(error -> getErrorResponseMono(error, List.class));

            /* Get all pages in edit mode post apply migrate DSL changes */
            Mono<ResponseDTO<List>> listOfAllPageResponseDTOMono = checkApiAccessIfAnonymousUser(
                            userMonoCache, UNPUBLISHED_PAGE_WITH_MIGRATED_DSL)
                    .flatMap(ignore -> migrateDslMonoCache
                            .flatMap(migrateDsl -> applicationPagesDTOResponseDTOMonoCache
                                    .map(ResponseDTO::getData)
                                    .map(ApplicationPagesDTO::getPages)
                                    .flatMapMany(Flux::fromIterable)
                                    .flatMap(
                                            page -> applicationPageService.getPageAndMigrateDslByBranchAndDefaultPageId(
                                                    page.getDefaultPageId(), branchName, false, migrateDsl))
                                    .collect(Collectors.toList()))
                            .map(res -> (List) res)
                            .map(this::getSuccessResponse))
                    .onErrorResume(error -> getErrorResponseMono(error, List.class));

            /* Get all workspace id */
            Mono<String> workspaceIdMonoCache = applicationPagesDTOResponseDTOMonoCache
                    .map(responseDTO -> {
                        if (HttpStatus.OK.value()
                                != responseDTO.getResponseMeta().getStatus()) {
                            return EMPTY_WORKSPACE_ID_ON_ERROR;
                        }

                        return responseDTO.getData().getWorkspaceId();
                    })
                    .onErrorResume(error -> Mono.just(EMPTY_WORKSPACE_ID_ON_ERROR))
                    .cache();

            /* Get all plugins in workspace */
            Mono<ResponseDTO<List>> listOfPluginsResponseDTOMonoCache = checkApiAccessIfAnonymousUser(
                            userMonoCache, PLUGINS)
                    .flatMap(ignore -> workspaceIdMonoCache
                            .flatMap(workspaceId -> {
                                MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                                if (!EMPTY_WORKSPACE_ID_ON_ERROR.equals(workspaceId)) {
                                    params.add(WORKSPACE_ID, workspaceId);
                                }
                                return pluginService.get(params).collectList();
                            })
                            .map(res -> (List) res)
                            .map(this::getSuccessResponse))
                    .onErrorResume(error -> getErrorResponseMono(error, List.class))
                    .cache();

            /* Get all datasources in workspace */
            Mono<ResponseDTO<List>> listOfDatasourcesResponseDTOMonoCache = checkApiAccessIfAnonymousUser(
                            userMonoCache, DATASOURCES)
                    .flatMap(ignore -> workspaceIdMonoCache
                            .flatMap(workspaceId -> {
                                MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                                if (!EMPTY_WORKSPACE_ID_ON_ERROR.equals(workspaceId)) {
                                    params.add(WORKSPACE_ID, workspaceId);
                                }
                                return datasourceService
                                        .getAllWithStorages(params)
                                        .collectList();
                            })
                            .map(res -> (List) res)
                            .map(this::getSuccessResponse))
                    .onErrorResume(error -> getErrorResponseMono(error, List.class))
                    .cache();

            /* Get form config for all relevant plugins by following this rule:
             *   (a) there is at least one datasource of the plugin type alive in the workspace
             *   (b) include REST API and GraphQL API plugin always
             *   (c) ignore any other plugin
             *  */
            Mono<ResponseDTO<Map>> listOfFormConfigsResponseDTOMono = checkApiAccessIfAnonymousUser(
                            userMonoCache, PLUGIN_FORM_CONFIGS)
                    .flatMap(ignore -> Mono.zip(
                                    listOfPluginsResponseDTOMonoCache, listOfDatasourcesResponseDTOMonoCache)
                            .map(tuple2 -> {
                                Set<String> setOfAllPluginIdsToGetFormConfig = new HashSet<>();
                                List<Plugin> pluginList = tuple2.getT1().getData();
                                List<Datasource> datasourcesList =
                                        tuple2.getT2().getData();

                                datasourcesList.stream()
                                        .filter(datasource -> !isBlank(datasource.getPluginId()))
                                        .forEach(datasource ->
                                                setOfAllPluginIdsToGetFormConfig.add(datasource.getPluginId()));

                                pluginList.stream()
                                        .filter(plugin -> REST_API_PLUGIN.equals(plugin.getPackageName())
                                                || GRAPHQL_PLUGIN.equals(plugin.getPackageName()))
                                        .forEach(plugin -> setOfAllPluginIdsToGetFormConfig.add(plugin.getId()));

                                return setOfAllPluginIdsToGetFormConfig;
                            })
                            .flatMapMany(Flux::fromIterable)
                            .flatMap(pluginId -> pluginService
                                    .getFormConfig(pluginId)
                                    .map(formConfig -> Pair.of(pluginId, formConfig)))
                            .collectList()
                            .map(listOfFormConfig -> {
                                Map<String, Map> pluginIdToFormConfigMap = new HashMap<>();
                                listOfFormConfig.stream().forEach(individualConfigMap -> {
                                    String pluginId = individualConfigMap.getFirst();
                                    Map config = individualConfigMap.getSecond();
                                    pluginIdToFormConfigMap.put(pluginId, config);
                                });

                                return pluginIdToFormConfigMap;
                            })
                            .map(res -> (Map) res)
                            .map(this::getSuccessResponse))
                    .onErrorResume(error -> getErrorResponseMono(error, Map.class));

            /* List of mock datasources available to the user */
            Mono<ResponseDTO<List>> mockDataListResponseDTOMono = checkApiAccessIfAnonymousUser(
                            userMonoCache, MOCK_DATASOURCES)
                    .flatMap(ignore -> mockDataService
                            .getMockDataSet()
                            .map(MockDataDTO::getMockdbs)
                            .map(res -> (List) res)
                            .map(this::getSuccessResponse))
                    .onErrorResume(error -> getErrorResponseMono(error, List.class));

            /* This list contains the Mono objects corresponding to all the data points required for edit mode. All
             * the Mono objects in this list will be evaluated via Mono.zip operator
             */
            List<Mono<?>> listOfMonoForEditMode = new ArrayList<>(List.of(
                    userProfileDTOResponseDTOMono,
                    tenantResponseDTOMono,
                    featureFlagsForCurrentUserResponseDTOMonoCache,
                    applicationPagesDTOResponseDTOMonoCache,
                    applicationThemeResponseDTOMono,
                    ThemesListResponseDTOMono,
                    allJSLibsInContextDTOResponseDTOMono,
                    productAlertResponseDTOMono,
                    listOfActionResponseDTOMono,
                    listOfActionCollectionResponseDTOMono,
                    listOfAllPageResponseDTOMono,
                    listOfPluginsResponseDTOMonoCache,
                    listOfDatasourcesResponseDTOMonoCache,
                    listOfFormConfigsResponseDTOMono,
                    mockDataListResponseDTOMono));

            if (!isBlank(defaultPageId)) {
                listOfMonoForEditMode.add(currentPageDTOResponseDTOMono);
            }
            return Mono.zip(listOfMonoForEditMode, responseArray -> {
                consolidatedAPIResponseDTO.setUserProfile((ResponseDTO<UserProfileDTO>) responseArray[0]);
                consolidatedAPIResponseDTO.setTenantConfig((ResponseDTO<Tenant>) responseArray[1]);
                consolidatedAPIResponseDTO.setFeatureFlags((ResponseDTO<Map<String, Boolean>>) responseArray[2]);
                consolidatedAPIResponseDTO.setPages((ResponseDTO<ApplicationPagesDTO>) responseArray[3]);
                consolidatedAPIResponseDTO.setCurrentTheme((ResponseDTO<Theme>) responseArray[4]);
                consolidatedAPIResponseDTO.setThemes((ResponseDTO<List<Theme>>) responseArray[5]);
                consolidatedAPIResponseDTO.setCustomJSLibraries((ResponseDTO<List<CustomJSLib>>) responseArray[6]);
                consolidatedAPIResponseDTO.setProductAlert((ResponseDTO<ProductAlertResponseDTO>) responseArray[7]);
                consolidatedAPIResponseDTO.setUnpublishedActions((ResponseDTO<List<ActionDTO>>) responseArray[8]);
                consolidatedAPIResponseDTO.setUnpublishedActionCollections(
                        (ResponseDTO<List<ActionCollectionDTO>>) responseArray[9]);
                consolidatedAPIResponseDTO.setPagesWithMigratedDsl((ResponseDTO<List<PageDTO>>) responseArray[10]);
                consolidatedAPIResponseDTO.setPlugins((ResponseDTO<List<Plugin>>) responseArray[11]);
                consolidatedAPIResponseDTO.setDatasources((ResponseDTO<List<Datasource>>) responseArray[12]);
                consolidatedAPIResponseDTO.setPluginFormConfigs((ResponseDTO<Map<String, Map>>) responseArray[13]);
                consolidatedAPIResponseDTO.setMockDatasources((ResponseDTO<List<MockDataSet>>) responseArray[14]);

                if (!isBlank(defaultPageId)) {
                    consolidatedAPIResponseDTO.setPageWithMigratedDsl((ResponseDTO<PageDTO>) responseArray[15]);
                }

                return consolidatedAPIResponseDTO;
            });
        }
    }
}
