import { fetchMockDatasources } from "actions/datasourceActions";
import {
  fetchGitProtectedBranchesInit,
  fetchGitStatusInit,
  remoteUrlInputValue,
  resetPullMergeStatus,
  fetchBranchesInit,
  startAutocommitProgressPolling,
  getGitMetadataInitAction,
} from "actions/gitSyncActions";
import { restoreRecentEntitiesRequest } from "actions/globalSearchActions";
import { resetEditorSuccess } from "actions/initActions";
import { fetchAllPageEntityCompletion, setupPage } from "actions/pageActions";
import {
  executePageLoadActions,
  fetchActions,
} from "actions/pluginActionActions";
import { fetchPluginFormConfigs } from "actions/pluginActions";
import type { ApplicationPayload } from "@appsmith/constants/ReduxActionConstants";
import {
  ReduxActionErrorTypes,
  ReduxActionTypes,
} from "@appsmith/constants/ReduxActionConstants";
import { addBranchParam } from "constants/routes";
import type { APP_MODE } from "entities/App";
import { call, fork, put, select, spawn } from "redux-saga/effects";
import {
  failFastApiCalls,
  reportSWStatus,
  waitForWidgetConfigBuild,
} from "entities/Engine/AppEngineUtils";
import { getCurrentApplication } from "selectors/editorSelectors";
import { getCurrentGitBranch } from "selectors/gitSyncSelectors";
import AnalyticsUtil from "utils/AnalyticsUtil";
import history from "utils/history";
import PerformanceTracker, {
  PerformanceTransactionName,
} from "utils/PerformanceTracker";
import type { AppEnginePayload } from ".";
import AppEngine, {
  ActionsNotFoundError,
  PluginFormConfigsNotFoundError,
  PluginsNotFoundError,
} from ".";
import { fetchJSLibraries } from "actions/JSLibraryActions";
import CodemirrorTernService from "utils/autocomplete/CodemirrorTernService";
import {
  waitForSegmentInit,
  waitForFetchUserSuccess,
} from "@appsmith/sagas/userSagas";
import { getFirstTimeUserOnboardingComplete } from "selectors/onboardingSelectors";
import { isAirgapped } from "@appsmith/utils/airgapHelpers";
import { getAIPromptTriggered } from "utils/storage";
import { trackOpenEditorTabs } from "../../utils/editor/browserTabsTracking";
import { EditorModes } from "components/editorComponents/CodeEditor/sql/config";
import { waitForFetchEnvironments } from "@appsmith/sagas/EnvironmentSagas";
import { getPageDependencyActions } from "@appsmith/entities/Engine/actionHelpers";
import { getCurrentWorkspaceId } from "@appsmith/selectors/selectedWorkspaceSelectors";
import {
  getFeatureFlagsForEngine,
  type DependentFeatureFlags,
} from "@appsmith/selectors/engineSelectors";
import { fetchJSCollections } from "actions/jsActionActions";
import {
  fetchAppThemesAction,
  fetchSelectedAppThemeAction,
} from "actions/appThemingActions";
import type { EditConsolidatedApi } from "api/ApiTypes";

export default class AppEditorEngine extends AppEngine {
  constructor(mode: APP_MODE) {
    super(mode);
    this.setupEngine = this.setupEngine.bind(this);
    this.loadAppData = this.loadAppData.bind(this);
    this.loadAppURL = this.loadAppURL.bind(this);
    this.loadAppEntities = this.loadAppEntities.bind(this);
    this.loadGit = this.loadGit.bind(this);
    this.completeChore = this.completeChore.bind(this);
    this.loadPageThemesAndActions = this.loadPageThemesAndActions.bind(this);
    this.loadPluginsAndDatasources = this.loadPluginsAndDatasources.bind(this);
  }

  /**
   * this saga is called once then application is loaded.
   * It will hold the editor in uninitialized till all the apis/actions are completed
   *
   * @param AppEnginePayload
   * @returns
   */
  public *setupEngine(payload: AppEnginePayload): any {
    yield* super.setupEngine.call(this, payload);
    yield put(resetEditorSuccess());
    CodemirrorTernService.resetServer();
  }

  public startPerformanceTracking() {
    PerformanceTracker.startAsyncTracking(
      PerformanceTransactionName.INIT_EDIT_APP,
    );
  }

  public stopPerformanceTracking() {
    PerformanceTracker.stopAsyncTracking(
      PerformanceTransactionName.INIT_EDIT_APP,
    );
  }

  private *loadPageThemesAndActions(
    toLoadPageId: string,
    applicationId: string,
    allResponses: EditConsolidatedApi,
  ) {
    const {
      currentTheme,
      customJSLibraries,
      pageWithMigratedDsl,
      themes,
      unpublishedActionCollections,
      unpublishedActions,
    } = allResponses;
    const initActionsCalls = [
      setupPage(toLoadPageId, true, pageWithMigratedDsl),
      fetchActions({ applicationId, unpublishedActions }, []),
      fetchJSCollections({ applicationId, unpublishedActionCollections }),
      fetchSelectedAppThemeAction(applicationId, currentTheme),
      fetchAppThemesAction(applicationId, themes),
    ];

    const successActionEffects = [
      ReduxActionTypes.FETCH_JS_ACTIONS_SUCCESS,
      ReduxActionTypes.FETCH_ACTIONS_SUCCESS,
      ReduxActionTypes.FETCH_APP_THEMES_SUCCESS,
      ReduxActionTypes.FETCH_SELECTED_APP_THEME_SUCCESS,
      ReduxActionTypes.SETUP_PAGE_SUCCESS,
    ];

    const failureActionEffects = [
      ReduxActionErrorTypes.FETCH_JS_ACTIONS_ERROR,
      ReduxActionErrorTypes.FETCH_ACTIONS_ERROR,
      ReduxActionErrorTypes.FETCH_APP_THEMES_ERROR,
      ReduxActionErrorTypes.FETCH_SELECTED_APP_THEME_ERROR,
      ReduxActionErrorTypes.SETUP_PAGE_ERROR,
    ];

    initActionsCalls.push(fetchJSLibraries(applicationId, customJSLibraries));
    successActionEffects.push(ReduxActionTypes.FETCH_JS_LIBRARIES_SUCCESS);

    const allActionCalls: boolean = yield call(
      failFastApiCalls,
      initActionsCalls,
      successActionEffects,
      failureActionEffects,
    );

    if (!allActionCalls)
      throw new ActionsNotFoundError(
        `Unable to fetch actions for the application: ${applicationId}`,
      );

    yield call(waitForFetchUserSuccess);
    yield call(waitForSegmentInit, true);
    yield call(waitForFetchEnvironments);
    yield put(fetchAllPageEntityCompletion([executePageLoadActions()]));
  }

  private *loadPluginsAndDatasources(allResponses: EditConsolidatedApi) {
    const { mockDatasources, pluginFormConfigs } = allResponses || {};
    const isAirgappedInstance = isAirgapped();
    const currentWorkspaceId: string = yield select(getCurrentWorkspaceId);
    const featureFlags: DependentFeatureFlags = yield select(
      getFeatureFlagsForEngine,
    );
    const { errorActions, initActions, successActions } =
      getPageDependencyActions(currentWorkspaceId, featureFlags, allResponses);

    if (!isAirgappedInstance) {
      initActions.push(fetchMockDatasources(mockDatasources));
      successActions.push(ReduxActionTypes.FETCH_MOCK_DATASOURCES_SUCCESS);
      errorActions.push(ReduxActionErrorTypes.FETCH_MOCK_DATASOURCES_ERROR);
    }

    const initActionCalls: boolean = yield call(
      failFastApiCalls,
      initActions,
      successActions,
      errorActions,
    );

    if (!initActionCalls)
      throw new PluginsNotFoundError("Unable to fetch plugins");

    const pluginFormCall: boolean = yield call(
      failFastApiCalls,
      [fetchPluginFormConfigs(pluginFormConfigs)],
      [ReduxActionTypes.FETCH_PLUGIN_FORM_CONFIGS_SUCCESS],
      [ReduxActionErrorTypes.FETCH_PLUGIN_FORM_CONFIGS_ERROR],
    );
    if (!pluginFormCall)
      throw new PluginFormConfigsNotFoundError(
        "Unable to fetch plugin form configs",
      );
  }

  public *loadAppEntities(
    toLoadPageId: string,
    applicationId: string,
    allResponses: EditConsolidatedApi,
  ): any {
    yield call(
      this.loadPageThemesAndActions,
      toLoadPageId,
      applicationId,
      allResponses,
    );
    yield call(this.loadPluginsAndDatasources, allResponses);
  }

  public *completeChore() {
    const isFirstTimeUserOnboardingComplete: boolean = yield select(
      getFirstTimeUserOnboardingComplete,
    );
    const currentApplication: ApplicationPayload = yield select(
      getCurrentApplication,
    );

    const [isAnotherEditorTabOpen, currentTabs] = yield call(
      trackOpenEditorTabs,
      currentApplication.id,
    );

    if (currentApplication) {
      AnalyticsUtil.logEvent("EDITOR_OPEN", {
        appId: currentApplication.id,
        appName: currentApplication.name,
        isAnotherEditorTabOpen,
        currentTabs,
      });
    }
    if (isFirstTimeUserOnboardingComplete) {
      yield put({
        type: ReduxActionTypes.SET_FIRST_TIME_USER_ONBOARDING_APPLICATION_IDS,
        payload: [],
      });
    }

    const noOfTimesAIPromptTriggered: number = yield getAIPromptTriggered(
      EditorModes.TEXT_WITH_BINDING,
    );

    yield put({
      type: ReduxActionTypes.UPDATE_AI_TRIGGERED,
      payload: {
        value: noOfTimesAIPromptTriggered,
        mode: EditorModes.TEXT_WITH_BINDING,
      },
    });

    const noOfTimesAIPromptTriggeredForQuery: number =
      yield getAIPromptTriggered(EditorModes.POSTGRESQL_WITH_BINDING);

    yield put({
      type: ReduxActionTypes.UPDATE_AI_TRIGGERED,
      payload: {
        value: noOfTimesAIPromptTriggeredForQuery,
        mode: EditorModes.POSTGRESQL_WITH_BINDING,
      },
    });

    yield call(waitForWidgetConfigBuild);
    yield spawn(reportSWStatus);

    yield put({
      type: ReduxActionTypes.INITIALIZE_EDITOR_SUCCESS,
    });
  }

  public *loadGit(applicationId: string) {
    const branchInStore: string = yield select(getCurrentGitBranch);
    yield put(
      restoreRecentEntitiesRequest({
        applicationId,
        branch: branchInStore,
      }),
    );
    // init of temporary remote url from old application
    yield put(remoteUrlInputValue({ tempRemoteUrl: "" }));
    // add branch query to path and fetch status
    if (branchInStore) {
      history.replace(addBranchParam(branchInStore));
      yield fork(this.loadGitInBackground);
    }
  }

  private *loadGitInBackground() {
    yield put(fetchBranchesInit());
    yield put(fetchGitProtectedBranchesInit());
    yield put(fetchGitProtectedBranchesInit());
    yield put(getGitMetadataInitAction());

    yield put(fetchGitStatusInit({ compareRemote: true }));

    yield put(startAutocommitProgressPolling());
    yield put(resetPullMergeStatus());
  }
}
