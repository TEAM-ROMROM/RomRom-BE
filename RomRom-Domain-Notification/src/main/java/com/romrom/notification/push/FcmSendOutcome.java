package com.romrom.notification.push;

import com.google.firebase.messaging.MessagingErrorCode;

/**
 * 메시지 1건 발송 결과: 성공 여부, 실패 오류 코드 (성공·코드 없음 > null)
 */
public record FcmSendOutcome(boolean isSuccessful, MessagingErrorCode errorCode) {

  public static FcmSendOutcome success() {
    return new FcmSendOutcome(true, null);
  }

  public static FcmSendOutcome failure(MessagingErrorCode errorCode) {
    return new FcmSendOutcome(false, errorCode);
  }
}
