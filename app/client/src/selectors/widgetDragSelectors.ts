import type { AppState } from "@appsmith/reducers";
import { createSelector } from "reselect";
import { getIsAppSettingsPaneWithNavigationTabOpen } from "./appSettingsPaneSelectors";
import {
  combinedPreviewModeSelector,
  snipingModeSelector,
} from "./editorSelectors";
import { getCanvasPreviewMode } from "./ideSelectors";

export const getIsDragging = (state: AppState) =>
  state.ui.widgetDragResize.isDragging;

export const getIsResizing = (state: AppState) =>
  state.ui.widgetDragResize.isResizing;

export const getIsDraggingDisabledInEditor = (state: AppState) =>
  state.ui.widgetDragResize.isDraggingDisabled;

/**
 * getShouldAllowDrag is a Selector that indicates if the widget could be dragged on canvas based on different states
 */
export const getShouldAllowDrag = createSelector(
  getIsResizing,
  getIsDragging,
  getIsDraggingDisabledInEditor,
  combinedPreviewModeSelector,
  getCanvasPreviewMode,
  snipingModeSelector,
  getIsAppSettingsPaneWithNavigationTabOpen,
  (
    isResizing,
    isDragging,
    isDraggingDisabled,
    isPreviewMode,
    isCanvasPreviewMode,
    isSnipingMode,
    isAppSettingsPaneWithNavigationTabOpen,
  ) => {
    return (
      !isResizing &&
      !isDragging &&
      !isDraggingDisabled &&
      !isSnipingMode &&
      !isPreviewMode &&
      !isCanvasPreviewMode &&
      !isAppSettingsPaneWithNavigationTabOpen
    );
  },
);
