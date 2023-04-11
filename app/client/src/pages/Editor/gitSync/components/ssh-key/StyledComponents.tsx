import styled from "styled-components";
import { Colors } from "constants/Colors";
import { Text } from "design-system-old";

export const TooltipWrapper = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  margin-top: -3px;
`;

export const DeployedKeyContainer = styled.div<{ $marginTop: number }>`
  margin-top: ${(props) => `${props.theme.spaces[props.$marginTop]}px`};
  margin-bottom: 8px;
  height: 35px;
  width: calc(100% - 30px);
  border: 1px solid ${Colors.ALTO_3};
  padding: ${(props) =>
    `${props.theme.spaces[3]}px ${props.theme.spaces[4]}px`};
  box-sizing: border-box;
  border-radius: var(--ads-v2-border-radius);
`;

export const FlexRow = styled.div`
  display: flex;
  flex-direction: row;
  width: 100%;
`;

export const ConfirmRegeneration = styled(FlexRow)`
  margin-top: 16.5px;
  justify-content: space-between;
`;

export const KeyType = styled.span<{ keyType: string }>`
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--appsmith-color-black-900);
`;

export const KeyText = styled.span<{ keyType: string }>`
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  flex: 1;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--appsmith-color-black-900);
  direction: rtl;
  margin-right: 8px;
`;

export const MoreMenuWrapper = styled.div`
  padding: 8px;
  align-items: center;
  position: absolute;
  right: -6px;
  top: 3px;
`;

export const ConfirmMenuItem = styled.div`
  padding: 16px 12px;
`;

export const NotificationBannerContainer = styled.div`
  max-width: calc(100% - 30px);
`;

export const StyledTextBlock = styled(Text)`
  display: block;
`;
