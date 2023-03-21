export * from "ce/reducers";
import type { AppState as CE_AppState } from "ce/reducers";
import { reducerObject as CE_AppReducer } from "ce/reducers";
import { combineReducers } from "redux";
import type { AclReduxState } from "./aclReducers";
import aclReducer from "./aclReducers";
import type { AuditLogsReduxState } from "./auditLogsReducer";
import auditLogsReducer from "./auditLogsReducer";
import type { EnvironmentsReduxState } from "./environmentReducer";
import environmentReducer from "./environmentReducer";

const appReducer = combineReducers({
  ...CE_AppReducer,
  acl: aclReducer,
  auditLogs: auditLogsReducer,
  environments: environmentReducer,
});

export interface AppState extends CE_AppState {
  acl: AclReduxState;
  auditLogs: AuditLogsReduxState;
  environments: EnvironmentsReduxState;
}

export default appReducer;
