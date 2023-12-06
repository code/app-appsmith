import { createReducer } from "utils/ReducerUtils";
import type { ReduxAction } from "@appsmith/constants/ReduxActionConstants";
import { ReduxActionTypes } from "@appsmith/constants/ReduxActionConstants";
import type {
  ConditionalOutput,
  FormEvalOutput,
  FormEvaluationState,
} from "./formEvaluationReducer";
import { merge } from "lodash";

// // Type for the object that will store the eval output for the app
export type TriggerValuesEvaluationState = Record<string, FormEvalOutput>;

export interface TriggerActionPayload {
  formId: string;
  values: ConditionalOutput;
}

export interface TriggerActionLoadingPayload {
  formId: string;
  keys: string[]; // keys that need their loading states set.
  value: boolean;
}

const initialState: TriggerValuesEvaluationState = {};

const triggers = createReducer(initialState, {
  [ReduxActionTypes.INIT_TRIGGER_VALUES]: (
    state: FormEvaluationState,
    action: ReduxAction<FormEvaluationState>,
  ): FormEvaluationState => action.payload,
  [ReduxActionTypes.FETCH_TRIGGER_VALUES_INIT]: (
    state: FormEvaluationState,
    action: ReduxAction<TriggerActionPayload>,
  ) => {
    const triggers = state[action.payload.formId];
    const newTriggers = merge({}, triggers, action.payload.values);
    return {
      ...state,
      [action.payload.formId]: newTriggers,
    };
  },
  [ReduxActionTypes.FETCH_TRIGGER_VALUES_SUCCESS]: (
    state: FormEvaluationState,
    action: ReduxAction<TriggerActionPayload>,
  ) => {
    const triggers = state[action.payload.formId];
    const newTriggers = merge({}, triggers, action.payload.values);
    return {
      ...state,
      [action.payload.formId]: newTriggers,
    };
  },
  [ReduxActionTypes.SET_TRIGGER_VALUES_LOADING]: (
    state: FormEvaluationState,
    action: ReduxAction<TriggerActionLoadingPayload>,
  ) => {
    const triggers = state[action.payload.formId];

    const triggersToBeFetched: FormEvalOutput = {};
    Object.entries(triggers).forEach(([key, value]) => {
      if (action.payload.keys.includes(key)) {
        const newValue = {
          ...value,
          fetchDynamicValues: {
            ...value.fetchDynamicValues,
            isLoading: action.payload.value,
          },
        };
        triggersToBeFetched[key] = newValue as unknown as FormEvalOutput;
      }
    });
    return {
      [action.payload.formId]: {
        ...triggers,
        ...triggersToBeFetched,
      },
    };
  },
});

export default triggers;
