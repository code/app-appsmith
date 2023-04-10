import { Collapse } from "@blueprintjs/core";
import { isString } from "lodash";
import type { Log, Message, SourceEntity } from "entities/AppsmithConsole";
import { LOG_CATEGORY, Severity } from "entities/AppsmithConsole";
import type { PropsWithChildren } from "react";
import React, { useState } from "react";
import ReactJson from "react-json-view";
import styled from "styled-components";
import EntityLink, { DebuggerLinkUI } from "./EntityLink";
import { getLogIcon } from "./helpers";
import { Classes, getTypographyByKey } from "design-system-old";
import {
  createMessage,
  TROUBLESHOOT_ISSUE,
} from "@appsmith/constants/messages";
import ContextualMenu from "./ContextualMenu";
import { Button, Icon, Tooltip } from "design-system";
import moment from "moment";

const InnerWrapper = styled.div`
  display: flex;
  align-items: center;
`;

const Wrapper = styled.div<{ collapsed: boolean }>`
  display: flex;
  flex-direction: column;
  padding: 8px 16px 8px 16px;

  &.${Severity.INFO} {
    border-bottom: 1px solid var(--ads-v2-color-border-info);
  }

  &.${Severity.ERROR} {
    background-color: #fff8f8;
    border-bottom: 1px solid var(--ads-v2-color-border-error);
  }

  &.${Severity.WARNING} {
    background-color: var(--ads-v2-color-bg-warning);
    border-bottom: 1px solid var(--ads-v2-color-border-warning);
  }

  .${Classes.ICON} {
    display: inline-block;
  }

  .debugger-toggle {
    ${(props) =>
      props.collapsed
        ? `transform: rotate(-90deg);`
        : `transform: rotate(0deg); `};
    padding-right: 4px;
  }
  .debugger-time {
    ${getTypographyByKey("h6")}
    letter-spacing: -0.24px;
    margin-left: 4px;
    margin-right: 4px;
    color: var(--ads-v2-color-fg-muted);
  }
  .debugger-occurences {
    height: 16px;
    width: 16px;
    border-radius: 36px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: var(--ads-v2-color-fg-emphasis);
    &.${Severity.INFO} {
      background-color: var(--ads-v2-color-bg-information);
    }
    margin-right: 4px;
    &.${Severity.ERROR} {
      background-color: var(--ads-v2-color-bg-error);
    }
    &.${Severity.WARNING} {
      background-color: var(--ads-v2-color-bg-warning);
    }
    ${getTypographyByKey("u2")}
  }
  .debugger-description {
    display: flex;
    align-items: center;
    overflow-wrap: anywhere;
    word-break: break-word;
    max-width: 60%;

    .debugger-label {
      color: var(--ads-v2-color-fg-emphasis);
      ${getTypographyByKey("p1")}
      line-height: 14px;
      font-size: 12px;
      padding-right: 4px;
      text-overflow: ellipsis;
      overflow: hidden;
      white-space: nowrap;
      -webkit-user-select: all; /* Chrome 49+ */
      -moz-user-select: all; /* Firefox 43+ */
      -ms-user-select: all; /* No support yet */
      user-select: all; /* Likely future */
    }
    .debugger-entity {
      color: var(--ads-v2-color-fg-emphasis);
      ${getTypographyByKey("h6")}
      margin-left: 6px;

      & > span {
        cursor: pointer;

        &:hover {
          text-decoration: underline;
          text-decoration-color: var(--ads-v2-color-fg-emphasis);
        }
      }
    }
  }
  .debugger-timetaken {
    color: var(--ads-v2-color-fg-emphasis);
    margin-left: 5px;
    ${getTypographyByKey("p2")}
    line-height: 19px;
  }

  .debugger-entity-link {
    // TODO: unclear why this file and ErrorLogItem.tsx have different styles when they look so similar
    margin-left: auto;
    ${getTypographyByKey("btnMedium")};
    color: var(--ads-v2-color-fg-emphasis);
    text-transform: uppercase;
    cursor: pointer;
    width: max-content;
  }
`;

const ContextWrapper = styled.div`
  height: 14px;
  display: flex;
  align-items: center;
`;

const JsonWrapper = styled.div`
  padding-top: ${(props) => props.theme.spaces[1]}px;
  svg {
    color: var(--ads-v2-color-fg-muted) !important;
    height: 12px !important;
    width: 12px !important;
    vertical-align: baseline !important;
  }
`;

type StyledCollapseProps = PropsWithChildren<{
  category: LOG_CATEGORY;
}>;

const StyledButton = styled(Button)<{ isVisible: boolean }>`
  visibility: ${(props) => (props.isVisible ? "visible" : "hidden")};
`;

const StyledCollapse = styled(Collapse)<StyledCollapseProps>`
  margin-top: ${(props) =>
    props.isOpen && props.category === LOG_CATEGORY.USER_GENERATED
      ? " -20px"
      : " 4px"};
  margin-left: 92px;

  .debugger-message {
    ${getTypographyByKey("p2")}
    line-height: 14px;
    letter-spacing: -0.24px;
    font-size: 12px;
    color: var(--ads-v2-color-fg-emphasis);
  }

  .${Classes.ICON} {
    margin-left: 10px;
  }
`;

const MessageWrapper = styled.div`
  line-height: 14px;
`;

const showToggleIcon = (e: Log) => {
  let output = !!e.state || !!e.messages;
  if (!output && e.logData && e.logData.length > 0) {
    e.logData.forEach((item) => {
      if (typeof item === "object") {
        output = true;
      }
    });
  }
  return output;
};

export const getLogItemProps = (e: Log) => {
  return {
    icon: getLogIcon(e) as string,
    timestamp: e.timestamp,
    source: e.source,
    label: e.text,
    logData: e.logData,
    category: e.category,
    timeTaken: e.timeTaken ? `${e.timeTaken}ms` : "",
    severity: e.severity,
    text: e.text,
    state: e.state,
    id: e.source ? e.source.id : undefined,
    messages: e.messages,
    collapsable: showToggleIcon(e),
    occurences: e.occurrenceCount || 1,
  };
};

type LogItemProps = {
  collapsable?: boolean;
  icon: string;
  timestamp: string;
  label: string;
  timeTaken: string;
  severity: Severity;
  text: string;
  category: LOG_CATEGORY;
  logData?: any[];
  state?: Record<string, any>;
  id?: string;
  source?: SourceEntity;
  expand?: boolean;
  messages?: Message[];
  occurences: number;
};

function LogItem(props: LogItemProps) {
  const [isOpen, setIsOpen] = useState(!!props.expand);
  const reactJsonProps = {
    name: null,
    enableClipboard: false,
    displayObjectSize: false,
    displayDataTypes: false,
    style: {
      fontFamily:
        "-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Oxygen, Ubuntu, Cantarell, Fira Sans, Droid Sans, Helvetica Neue",
      fontSize: "11px",
      fontWeight: "400",
      letterSpacing: "-0.195px",
      lineHeight: "13px",
    },
    collapsed: 1,
  };

  const messages = props.messages || [];
  const { collapsable } = props;
  return (
    <Wrapper
      className={props.severity}
      collapsed={!isOpen}
      onClick={() => {
        if (collapsable) setIsOpen(!isOpen);
      }}
    >
      <InnerWrapper>
        <Button
          isDisabled={collapsable}
          isIconButton
          kind={props.severity === Severity.ERROR ? "error" : "tertiary"}
          size="md"
          startIcon={props.icon}
        />
        <span className={`debugger-time ${props.severity}`}>
          {props.severity === Severity.ERROR
            ? moment(parseInt(props.timestamp)).format("HH:mm:ss")
            : props.timestamp}
        </span>

        <StyledButton
          className={`${Classes.ICON} debugger-toggle`}
          isDisabled={collapsable}
          isIconButton
          isVisible={!collapsable}
          kind="tertiary"
          name={"expand-more"}
          onClick={() => setIsOpen(!isOpen)}
          size="md"
        />
        {!(
          props.collapsable &&
          isOpen &&
          props.category === LOG_CATEGORY.USER_GENERATED
        ) && (
          <div className="debugger-description">
            {props.occurences > 1 && (
              <span
                className={`t--debugger-log-message-occurence debugger-occurences ${props.severity}`}
              >
                {props.occurences}
              </span>
            )}
            <span
              className="debugger-label t--debugger-log-message"
              onClick={(e) => e.stopPropagation()}
            >
              {props.text}
            </span>

            {props.timeTaken && (
              <span className={`debugger-timetaken ${props.severity}`}>
                {props.timeTaken}
              </span>
            )}
            {props.category === LOG_CATEGORY.PLATFORM_GENERATED &&
              props.severity === Severity.ERROR && (
                <ContextWrapper onClick={(e) => e.stopPropagation()}>
                  <ContextualMenu
                    entity={props.source}
                    error={{ message: { name: "", message: "" } }}
                  >
                    <Tooltip
                      content={createMessage(TROUBLESHOOT_ISSUE)}
                      placement="bottomLeft"
                    >
                      <Icon
                        className={`${Classes.ICON}`}
                        name={"help"}
                        size="sm"
                      />
                    </Tooltip>
                  </ContextualMenu>
                </ContextWrapper>
              )}
          </div>
        )}
        {props.source && (
          <EntityLink
            id={props.source.id}
            name={props.source.name}
            type={props.source.type}
            uiComponent={DebuggerLinkUI.ENTITY_NAME}
          />
        )}
      </InnerWrapper>

      {collapsable && isOpen && (
        <StyledCollapse
          category={props.category}
          isOpen={isOpen}
          keepChildrenMounted
        >
          {messages.map((e) => {
            return (
              <MessageWrapper
                key={e.message.message}
                onClick={(e) => e.stopPropagation()}
              >
                <span className="debugger-message t--debugger-message">
                  {isString(e.message) ? e.message : e.message.message}
                </span>
              </MessageWrapper>
            );
          })}
          {props.state && (
            <JsonWrapper
              className="t--debugger-log-state"
              onClick={(e) => e.stopPropagation()}
            >
              <ReactJson src={props.state} {...reactJsonProps} />
            </JsonWrapper>
          )}
          {props.logData &&
            props.logData.length > 0 &&
            props.logData.map((logDatum: any) => {
              if (typeof logDatum === "object") {
                return (
                  <JsonWrapper
                    className="t--debugger-console-log-data"
                    key={Math.random()}
                    onClick={(e) => e.stopPropagation()}
                  >
                    <ReactJson src={logDatum} {...reactJsonProps} />
                  </JsonWrapper>
                );
              } else {
                return (
                  <span className="debugger-label" key={Math.random()}>
                    {`${logDatum} `}
                  </span>
                );
              }
            })}
        </StyledCollapse>
      )}
    </Wrapper>
  );
}

export default LogItem;
