package com.romrom.notification.push;

/**
 * 일괄 발송 집계: 성공 수, 실패 수, 삭제한 무효 토큰 수
 */
public record FcmBatchSendResult(int successCount, int failureCount, int removedTokenCount) {

  public static FcmBatchSendResult empty() {
    return new FcmBatchSendResult(0, 0, 0);
  }

  public FcmBatchSendResult plus(FcmBatchSendResult other) {
    return new FcmBatchSendResult(successCount + other.successCount, failureCount + other.failureCount,
        removedTokenCount + other.removedTokenCount);
  }
}
