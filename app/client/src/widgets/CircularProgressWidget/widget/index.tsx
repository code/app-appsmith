import * as React from "react";

import { ValidationTypes } from "constants/types";
import type { WidgetProps, WidgetState } from "widgets/BaseWidget";
import BaseWidget from "widgets/BaseWidget";
import { AutocompleteDataType } from "utils/autocomplete/AutocompleteDataType";
import type { CircularProgressComponentProps } from "../component";
import CircularProgressComponent from "../component";
import type { Stylesheet } from "entities/AppTheming";
import { DefaultAutocompleteDefinitions } from "widgets/WidgetUtils";
import type { AutocompletionDefinitions } from "WidgetProvider/constants";
import { Colors } from "constants/Colors";
import IconSVG from "../icon.svg";

interface CircularProgressWidgetProps
  extends WidgetProps,
    CircularProgressComponentProps {
  borderRadius?: string;
}

class CircularProgressWidget extends BaseWidget<
  CircularProgressWidgetProps,
  WidgetState
> {
  static type = "CIRCULAR_PROGRESS_WIDGET";

  static getConfig() {
    return {
      name: "Circular Progress",
      hideCard: true,
      isDeprecated: true,
      replacement: "PROGRESS_WIDGET",
      iconSVG: IconSVG,
    };
  }

  static getDefaults() {
    return {
      counterClockWise: false,
      fillColor: Colors.GREEN,
      isVisible: true,
      progress: 65,
      showResult: true,

      rows: 17,
      columns: 16,
      widgetName: "CircularProgress",
      shouldScroll: false,
      shouldTruncate: false,
      version: 1,
      animateLoading: true,
    };
  }

  static getPropertyPaneConfig() {
    return [
      {
        sectionName: "General",
        children: [
          {
            propertyName: "progress",
            helpText: "Sets the progress value of the widget",
            label: "Progress",
            controlType: "INPUT_TEXT",
            placeholderText: "Value:",
            isBindProperty: true,
            isTriggerProperty: false,
            validation: { type: ValidationTypes.NUMBER },
          },
          {
            propertyName: "counterClockwise",
            helpText: "Counter clock wise",
            label: "CounterClockWise",
            controlType: "SWITCH",
            isBindProperty: true,
            isTriggerProperty: false,
            validation: { type: ValidationTypes.BOOLEAN },
          },
          {
            propertyName: "showResult",
            helpText: "Controls the visibility of progress value",
            label: "Show result",
            controlType: "SWITCH",
            isJSConvertible: true,
            isBindProperty: true,
            isTriggerProperty: false,
            validation: { type: ValidationTypes.BOOLEAN },
          },
          {
            propertyName: "isVisible",
            helpText: "Controls the visibility of the widget",
            label: "Visible",
            controlType: "SWITCH",
            isJSConvertible: true,
            isBindProperty: true,
            isTriggerProperty: false,
            validation: { type: ValidationTypes.BOOLEAN },
          },
        ],
      },
      {
        sectionName: "Styles",
        children: [
          {
            propertyName: "fillColor",
            label: "Fill color",
            controlType: "COLOR_PICKER",
            isJSConvertible: true,
            isBindProperty: true,
            isTriggerProperty: false,
            validation: {
              type: ValidationTypes.TEXT,
              params: {
                regex: /^((?![<|{{]).+){0,1}/,
                expected: {
                  type: "string (HTML color name or HEX value)",
                  example: `red | #9C0D38`,
                  autocompleteDataType: AutocompleteDataType.STRING,
                },
              },
            },
          },
        ],
      },
    ];
  }

  static getStylesheetConfig(): Stylesheet {
    return {
      fillColor: "{{appsmith.theme.colors.primaryColor}}",
      borderRadius: "{{appsmith.theme.borderRadius.appBorderRadius}}",
    };
  }

  static getAutocompleteDefinitions(): AutocompletionDefinitions {
    return {
      "!doc": "Circular Progress is a simple UI widget used to show progress",
      "!url": "https://docs.appsmith.com/widget-reference/circular-progress",
      isVisible: DefaultAutocompleteDefinitions.isVisible,
      progress: "number",
    };
  }

  getWidgetView() {
    return (
      <CircularProgressComponent
        counterClockwise={this.props.counterClockwise}
        fillColor={this.props.fillColor}
        progress={this.props.progress}
        showResult={this.props.showResult}
      />
    );
  }
}

export default CircularProgressWidget;
