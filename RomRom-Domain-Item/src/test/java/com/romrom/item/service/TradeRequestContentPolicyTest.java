package com.romrom.item.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.romrom.common.constant.ItemTradeOption;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.exception.UgcProhibitedContentException;
import com.romrom.common.service.UgcFilterService;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 거래 요청 입력값 정리 단위 테스트 (#859): 추가금 범위·옵션 연동, 메시지 공백·길이·제어문자·비속어
 */
@ExtendWith(MockitoExtension.class)
class TradeRequestContentPolicyTest {

  @Mock
  private UgcFilterService ugcFilterService;

  @InjectMocks
  private TradeRequestContentPolicy tradeRequestContentPolicy;

  @Test
  @DisplayName("추가금: EXTRA_CHARGE 선택 + 범위 안 > 그대로 저장")
  void resolveExtraChargeAmount_keepsAmountInRange() {
    List<ItemTradeOption> extraChargeOptions = List.of(ItemTradeOption.EXTRA_CHARGE);
    Assertions.assertEquals(0, tradeRequestContentPolicy.resolveExtraChargeAmount(extraChargeOptions, 0));
    Assertions.assertEquals(10_000_000,
        tradeRequestContentPolicy.resolveExtraChargeAmount(extraChargeOptions, 10_000_000));
  }

  @Test
  @DisplayName("추가금: 옵션 미선택·미입력 > null (구버전 앱 요청 호환)")
  void resolveExtraChargeAmount_returnsNullWithoutOptionOrAmount() {
    Assertions.assertNull(tradeRequestContentPolicy.resolveExtraChargeAmount(List.of(ItemTradeOption.DIRECT_ONLY), 5000));
    Assertions.assertNull(tradeRequestContentPolicy.resolveExtraChargeAmount(null, 5000));
    Assertions.assertNull(tradeRequestContentPolicy.resolveExtraChargeAmount(List.of(ItemTradeOption.EXTRA_CHARGE), null));
  }

  @Test
  @DisplayName("추가금: 음수·상한 초과 > INVALID_TRADE_EXTRA_CHARGE_AMOUNT")
  void resolveExtraChargeAmount_rejectsOutOfRange() {
    List<ItemTradeOption> extraChargeOptions = List.of(ItemTradeOption.EXTRA_CHARGE);
    CustomException negativeAmountException = Assertions.assertThrows(CustomException.class,
        () -> tradeRequestContentPolicy.resolveExtraChargeAmount(extraChargeOptions, -1));
    Assertions.assertEquals(ErrorCode.INVALID_TRADE_EXTRA_CHARGE_AMOUNT, negativeAmountException.getErrorCode());
    CustomException overLimitException = Assertions.assertThrows(CustomException.class,
        () -> tradeRequestContentPolicy.resolveExtraChargeAmount(extraChargeOptions, 10_000_001));
    Assertions.assertEquals(ErrorCode.INVALID_TRADE_EXTRA_CHARGE_AMOUNT, overLimitException.getErrorCode());
  }

  @Test
  @DisplayName("메시지: 앞뒤 공백 제거, 공백만 > null")
  void resolveRequestMessage_trimsAndNormalizesBlank() {
    Assertions.assertEquals("주말 직거래 가능해요", tradeRequestContentPolicy.resolveRequestMessage("  주말 직거래 가능해요 "));
    Assertions.assertNull(tradeRequestContentPolicy.resolveRequestMessage("   "));
    Assertions.assertNull(tradeRequestContentPolicy.resolveRequestMessage(null));
    verify(ugcFilterService, never()).validate(eq("   "), anyString());
  }

  @Test
  @DisplayName("메시지: 100자 허용, 101자 > INVALID_TRADE_REQUEST_MESSAGE (이모지는 1자)")
  void resolveRequestMessage_limitsLengthByCodePoint() {
    String hundredEmojiMessage = "😀".repeat(100);
    Assertions.assertEquals(hundredEmojiMessage, tradeRequestContentPolicy.resolveRequestMessage(hundredEmojiMessage));
    CustomException tooLongException = Assertions.assertThrows(CustomException.class,
        () -> tradeRequestContentPolicy.resolveRequestMessage("가".repeat(101)));
    Assertions.assertEquals(ErrorCode.INVALID_TRADE_REQUEST_MESSAGE, tooLongException.getErrorCode());
  }

  @Test
  @DisplayName("메시지: 줄바꿈·제어문자·줄 구분자 > INVALID_TRADE_REQUEST_MESSAGE")
  void resolveRequestMessage_rejectsControlCharacters() {
    for (String invalidMessage : List.of("첫 줄\n둘째 줄", "탭\t포함", "널\u0000문자", "줄 구분")) {
      CustomException controlCharacterException = Assertions.assertThrows(CustomException.class,
          () -> tradeRequestContentPolicy.resolveRequestMessage(invalidMessage));
      Assertions.assertEquals(ErrorCode.INVALID_TRADE_REQUEST_MESSAGE, controlCharacterException.getErrorCode());
    }
  }

  @Test
  @DisplayName("메시지: 비속어 필터 위반 > UGC 예외 전달")
  void resolveRequestMessage_appliesUgcFilter() {
    doThrow(new UgcProhibitedContentException("나쁜말", "requestMessage"))
        .when(ugcFilterService).validate("나쁜말", "requestMessage");
    Assertions.assertThrows(UgcProhibitedContentException.class,
        () -> tradeRequestContentPolicy.resolveRequestMessage("나쁜말"));
  }
}
