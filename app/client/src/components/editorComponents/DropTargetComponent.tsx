import React, {
  Context,
  createContext,
  // memo,
  useEffect,
  useRef,
  useCallback,
  useMemo,
  PropsWithChildren,
} from "react";
import styled from "styled-components";
import equal from "fast-deep-equal/es6";
// import { WidgetProps } from "widgets/BaseWidget";
import { getCanvasSnapRows } from "utils/WidgetPropsUtils";
import {
  MAIN_CONTAINER_WIDGET_ID,
  GridDefaults,
} from "constants/WidgetConstants";
import { calculateDropTargetRows } from "./DropTargetUtils";
import DragLayerComponent from "./DragLayerComponent";
import { AppState } from "@appsmith/reducers";
import { useDispatch, useSelector } from "react-redux";
import {
  useShowPropertyPane,
  // useCanvasSnapRowsUpdateHook,
} from "utils/hooks/dragResizeHooks";
import {
  getOccupiedSpacesSelectorForContainer,
  previewModeSelector,
} from "selectors/editorSelectors";
import { useWidgetSelection } from "utils/hooks/useWidgetSelection";
import { getDragDetails } from "sagas/selectors";
import { useAutoHeightUIState } from "utils/hooks/autoHeightUIHooks";
import { immediatelyUpdateAutoHeightAction } from "actions/autoHeightActions";
import { isAutoHeightEnabledForWidget } from "widgets/WidgetUtils";

type DropTargetComponentProps = PropsWithChildren<{
  snapColumnSpace: number;
  widgetId: string;
  parentId?: string;
  noPad?: boolean;
  bottomRow: number;
  minHeight: number;
}>;

const StyledDropTarget = styled.div`
  transition: height 100ms ease-in;
  width: 100%;
  position: relative;
  background: none;
  user-select: none;
  z-index: 1;
`;

function Onboarding() {
  return (
    <h2 className="absolute top-0 left-0 right-0 flex items-end h-108 justify-center text-2xl font-bold text-gray-300">
      Drag and drop a widget here
    </h2>
  );
}

/*
  This context will provide the function which will help the draglayer and resizablecomponents trigger
  an update of the main container's rows
*/
export const DropTargetContext: Context<{
  updateDropTargetRows?: (
    widgetIdsToExclude: string[],
    widgetBottomRow: number,
  ) => number | false;
}> = createContext({});

/**
 * Gets the dropTarget height
 * @param canDropTargetExtend boolean: Can we put widgets below the scrollview in this canvas?
 * @param isPreviewMode boolean: Are we in the preview mode
 * @param currentHeight number: Current height in the ref and what we have set in the dropTarget
 * @param snapRowSpace number: This is a static value actually, GridDefaults.DEFAULT_GRID_ROW_HEIGHT
 * @param minHeight number: The minHeight we've set to the widget in the reducer
 * @returns number: A new height style to set in the dropTarget.
 */
function getDropTargetHeight(currentHeight: number) {
  const height = `${currentHeight * GridDefaults.DEFAULT_GRID_ROW_HEIGHT}px`;
  return height;
}

const updateHeight = (
  ref: React.MutableRefObject<HTMLDivElement | null>,
  currentRows: number,
  isParentAutoHeightEnabled: boolean,
) => {
  if (ref.current && !isParentAutoHeightEnabled) {
    const height = getDropTargetHeight(currentRows);
    ref.current.style.height = height;
  }
};

function useUpdateRows(bottomRow: number, widgetId: string, parentId?: string) {
  // This gives us the number of rows
  const snapRows = getCanvasSnapRows(bottomRow);
  // Put the existing snap rows in a ref.
  const rowRef = useRef(snapRows);

  const dropTargetRef = useRef<HTMLDivElement>(null);

  // The occupied spaces in this canvas. It is a data structure which has the rect values of each child.
  const selectOccupiedSpaces = useCallback(
    getOccupiedSpacesSelectorForContainer(widgetId),
    [widgetId],
  );

  // Call the selector above.
  const occupiedSpacesByChildren = useSelector(selectOccupiedSpaces, equal);
  /*
   * If the parent has auto height enabled, or if the current widget is the MAIN_CONTAINER_WIDGET_ID
   */
  const isParentAutoHeightEnabled = useSelector((state: AppState) => {
    return parentId
      ? !isAutoHeightEnabledForWidget(
          state.entities.canvasWidgets[parentId],
          true,
        ) &&
          isAutoHeightEnabledForWidget(state.entities.canvasWidgets[parentId])
      : false;
  });
  const dispatch = useDispatch();
  // Function which computes and updates the height of the dropTarget
  // This is used in a context and hence in one of the children of this dropTarget
  const updateDropTargetRows = (
    widgetIdsToExclude: string[],
    widgetBottomRow: number,
  ) => {
    const newRows = calculateDropTargetRows(
      widgetIdsToExclude,
      widgetBottomRow,
      occupiedSpacesByChildren,
      widgetId,
    );

    if (rowRef.current < newRows) {
      rowRef.current = newRows;
      if (isParentAutoHeightEnabled || widgetId === MAIN_CONTAINER_WIDGET_ID) {
        dispatch(
          immediatelyUpdateAutoHeightAction(parentId || widgetId, newRows),
        );
      }
      if (!isParentAutoHeightEnabled && widgetId !== MAIN_CONTAINER_WIDGET_ID)
        updateHeight(dropTargetRef, rowRef.current, isParentAutoHeightEnabled);
      return newRows;
    }
    return false;
  };
  // memoizing context values
  const contextValue = useMemo(() => {
    return {
      updateDropTargetRows,
    };
  }, [updateDropTargetRows, occupiedSpacesByChildren]);

  /** EO PREPARE CONTEXT */
  return { contextValue, dropTargetRef, rowRef };
}

export function DropTargetComponent(props: DropTargetComponentProps) {
  // Get if this is in preview mode.
  const isPreviewMode = useSelector(previewModeSelector);

  const { contextValue, dropTargetRef, rowRef } = useUpdateRows(
    props.bottomRow,
    props.widgetId,
    props.parentId,
  );

  // Are we currently resizing?
  const isResizing = useSelector(
    (state: AppState) => state.ui.widgetDragResize.isResizing,
  );
  // Are we currently dragging?
  const isDragging = useSelector(
    (state: AppState) => state.ui.widgetDragResize.isDragging,
  );
  // Are we changing the auto height limits by dragging the signifiers?
  const { isAutoHeightWithLimitsChanging } = useAutoHeightUIState();

  // dragDetails contains of info needed for a container jump:
  // which parent the dragging widget belongs,
  // which canvas is active(being dragged on),
  // which widget is grabbed while dragging started,
  // relative position of mouse pointer wrt to the last grabbed widget.
  const dragDetails = useSelector(getDragDetails);

  const { draggedOn } = dragDetails;

  // All the widgets in this canvas
  const childWidgets: string[] | undefined = useSelector(
    (state: AppState) => state.entities.canvasWidgets[props.widgetId]?.children,
  );

  // This shows the property pane
  const showPropertyPane = useShowPropertyPane();

  const { deselectAll, focusWidget } = useWidgetSelection();

  // Everytime we get a new bottomRow, or we toggle shouldScrollContents
  // we call this effect
  useEffect(() => {
    const snapRows = getCanvasSnapRows(props.bottomRow);

    // If the current ref is not set to the new snaprows we've received (based on bottomRow)
    if (rowRef.current !== snapRows) {
      rowRef.current = snapRows;
      updateHeight(dropTargetRef, snapRows, false);
    }
  }, [props.bottomRow, isPreviewMode]);

  const handleFocus = (e: any) => {
    // Making sure that we don't deselect the widget
    // after we are done dragging the limits in auto height with limits
    if (!isResizing && !isDragging && !isAutoHeightWithLimitsChanging) {
      if (!props.parentId) {
        deselectAll();
        focusWidget && focusWidget(props.widgetId);
        showPropertyPane && showPropertyPane();
      }
    }
    e.preventDefault();
  };

  const height = getDropTargetHeight(rowRef.current);

  const boxShadow =
    (isResizing || isDragging || isAutoHeightWithLimitsChanging) &&
    props.widgetId === MAIN_CONTAINER_WIDGET_ID
      ? "inset 0px 0px 0px 1px #DDDDDD"
      : "0px 0px 0px 1px transparent";

  const dropTargetStyles = {
    height,
    boxShadow,
  };

  const shouldOnboard =
    !(childWidgets && childWidgets.length) && !isDragging && !props.parentId;

  // The drag layer is the one with the grid dots.
  // They need to show in certain scenarios
  const showDragLayer =
    ((isDragging && draggedOn === props.widgetId) ||
      isResizing ||
      isAutoHeightWithLimitsChanging) &&
    !isPreviewMode;

  return (
    <DropTargetContext.Provider value={contextValue}>
      <StyledDropTarget
        className={`t--drop-target drop-target-${props.parentId ||
          MAIN_CONTAINER_WIDGET_ID}`}
        onClick={handleFocus}
        ref={dropTargetRef}
        style={dropTargetStyles}
      >
        {props.children}
        {shouldOnboard && <Onboarding />}
        {showDragLayer && (
          <DragLayerComponent
            noPad={props.noPad || false}
            parentColumnWidth={props.snapColumnSpace}
          />
        )}
      </StyledDropTarget>
    </DropTargetContext.Provider>
  );
}

export default DropTargetComponent;
