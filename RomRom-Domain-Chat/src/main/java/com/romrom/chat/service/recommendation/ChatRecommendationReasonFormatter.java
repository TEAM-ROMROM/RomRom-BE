package com.romrom.chat.service.recommendation;

import com.romrom.chat.dto.ChatRecommendedAction;
import org.springframework.util.StringUtils;

/**
 * 추천 이유 문구: 액션별 기본 이유, 한 줄·길이 제한 정리
 */
public final class ChatRecommendationReasonFormatter {

  // 추천 이유 최대 길이 (글자 수)
  private static final int MAXIMUM_REASON_LENGTH = 160;

  private ChatRecommendationReasonFormatter() {
  }

  /**
   * 추천 이유 정리: 비어 있으면 기본 이유 > 개행 제거 > 160자 제한
   */
  public static String normalizeReason(ChatRecommendedAction action, String reason) {
    if (!StringUtils.hasText(reason)) {
      return resolveDefaultReason(action);
    }
    String singleLineReason = reason.replace('\n', ' ').replace('\r', ' ').trim();
    return singleLineReason.length() > MAXIMUM_REASON_LENGTH
        ? singleLineReason.substring(0, MAXIMUM_REASON_LENGTH)
        : singleLineReason;
  }

  /**
   * 액션 > 프론트 표시용 기본 이유 (NONE·null 은 null)
   */
  public static String resolveDefaultReason(ChatRecommendedAction action) {
    if (action == null || action.isNone()) {
      return null;
    }
    return switch (action) {
      case SEND_LOCATION -> "만날 장소를 정하는 흐름이라 위치 공유를 추천해요.";
      case REQUEST_TRADE_COMPLETION -> "거래가 마무리된 분위기라 교환 완료 요청을 추천해요.";
      case CANCEL_TRADE_COMPLETION_REQUEST -> "이미 보낸 교환 완료 요청을 취소할 수 있어요.";
      case REJECT_TRADE_COMPLETION_REQUEST -> "상대방의 교환 완료 요청을 거절할 수 있어요.";
      case CONFIRM_TRADE_COMPLETION -> "상대방의 교환 완료 요청을 확인하고 완료 처리할 수 있어요.";
      case NONE -> null;
    };
  }
}
