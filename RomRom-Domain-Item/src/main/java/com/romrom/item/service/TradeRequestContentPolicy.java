package com.romrom.item.service;

import com.romrom.common.constant.ItemTradeOption;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.service.UgcFilterService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 거래 요청 입력값 정리: 추가금 범위 검증, 한 줄 메시지 정리·검증 (#859)
 * 두 값 모두 선택 > 구버전 앱 미전송 시 null 저장
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeRequestContentPolicy {

  // 추가금 허용 범위 (원)
  static final int MINIMUM_EXTRA_CHARGE_AMOUNT = 0;
  static final int MAXIMUM_EXTRA_CHARGE_AMOUNT = 10_000_000;
  // 한 줄 메시지 최대 길이 (코드포인트 기준, DB varchar(100)과 동일)
  static final int MAXIMUM_REQUEST_MESSAGE_LENGTH = 100;

  private final UgcFilterService ugcFilterService;

  /**
   * 추가금 > 저장값 (EXTRA_CHARGE 미선택·미입력 > null, 범위 밖 > 400)
   */
  public Integer resolveExtraChargeAmount(List<ItemTradeOption> itemTradeOptions, Integer requestedExtraChargeAmount) {
    boolean hasExtraChargeOption = itemTradeOptions != null && itemTradeOptions.contains(ItemTradeOption.EXTRA_CHARGE);
    if (!hasExtraChargeOption || requestedExtraChargeAmount == null) {
      return null;
    }
    if (requestedExtraChargeAmount < MINIMUM_EXTRA_CHARGE_AMOUNT
        || requestedExtraChargeAmount > MAXIMUM_EXTRA_CHARGE_AMOUNT) {
      log.warn("[trade request] rejected 400 : extra charge amount out of range ({})", requestedExtraChargeAmount);
      throw new CustomException(ErrorCode.INVALID_TRADE_EXTRA_CHARGE_AMOUNT);
    }
    return requestedExtraChargeAmount;
  }

  /**
   * 한 줄 메시지 > 저장값 (앞뒤 공백 제거, 빈 값 > null, 길이·제어문자·비속어 검증)
   */
  public String resolveRequestMessage(String rawRequestMessage) {
    if (rawRequestMessage == null) {
      return null;
    }
    String trimmedRequestMessage = rawRequestMessage.strip();
    if (trimmedRequestMessage.isEmpty()) {
      return null;
    }
    int requestMessageLength = trimmedRequestMessage.codePointCount(0, trimmedRequestMessage.length());
    if (requestMessageLength > MAXIMUM_REQUEST_MESSAGE_LENGTH) {
      log.warn("[trade request] rejected 400 : request message too long ({})", requestMessageLength);
      throw new CustomException(ErrorCode.INVALID_TRADE_REQUEST_MESSAGE);
    }
    if (hasControlCharacter(trimmedRequestMessage)) {
      log.warn("[trade request] rejected 400 : request message has control character");
      throw new CustomException(ErrorCode.INVALID_TRADE_REQUEST_MESSAGE);
    }
    ugcFilterService.validate(trimmedRequestMessage, "requestMessage");
    return trimmedRequestMessage;
  }

  // 줄바꿈·탭 포함 제어문자 + 유니코드 줄/문단 구분자 (한 줄 표시 보장)
  private boolean hasControlCharacter(String requestMessage) {
    return requestMessage.codePoints().anyMatch(codePoint ->
        Character.isISOControl(codePoint)
            || Character.getType(codePoint) == Character.LINE_SEPARATOR
            || Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR);
  }
}
