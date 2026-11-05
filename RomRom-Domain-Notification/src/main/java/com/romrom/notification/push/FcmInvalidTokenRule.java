package com.romrom.notification.push;

import com.google.firebase.messaging.MessagingErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 무효 토큰 판정 3개: UNREGISTERED, SENDER_ID_MISMATCH, INVALID_ARGUMENT(같은 묶음에 성공 1건 이상일 때만)
 * INVALID_ARGUMENT 는 메시지 자체 오류(이미지 주소 등)에도 나와, 묶음 전체 실패면 토큰 문제로 보지 않음
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FcmInvalidTokenRule {

  public static boolean isInvalidToken(MessagingErrorCode errorCode, boolean hasSuccessInSameChunk) {
    if (errorCode == null) {
      return false;
    }
    return switch (errorCode) {
      case UNREGISTERED, SENDER_ID_MISMATCH -> true;
      case INVALID_ARGUMENT -> hasSuccessInSameChunk;
      default -> false;
    };
  }
}
