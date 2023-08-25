import { Classes } from "@blueprintjs/core";
import ErrorBoundary from "components/editorComponents/ErrorBoundry";
import React from "react";
import type { BaseWidgetProps } from "widgets/BaseWidgetHOC/withBaseWidgetHOC";
import { ModalOverlayLayer } from "../../common/ModalOverlayLayer";
import { AutoLayoutWidgetComponent } from "../common/AutoLayoutWidgetNameComponent";

export const AutoLayoutViewerModalOnion = (props: BaseWidgetProps) => {
  return (
    <ErrorBoundary>
      <AutoLayoutWidgetComponent {...props}>
        <ModalOverlayLayer {...props} isEditMode={false}>
          <div className={Classes.OVERLAY_CONTENT}>{props.children}</div>
        </ModalOverlayLayer>
      </AutoLayoutWidgetComponent>
    </ErrorBoundary>
  );
};
