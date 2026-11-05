package com.romrom.notification.push;

import com.google.firebase.messaging.Message;
import com.romrom.notification.repository.FcmTokenRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * FCM 일괄 발송: 500건 묶음 sendEach > 결과 집계 > 무효 토큰 묶음별 1회 삭제
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FcmBatchSender {

  // FCM sendEach 1회 최대 메시지 수
  public static final int MAXIMUM_MESSAGES_PER_BATCH = 500;

  private final FcmMessagingClient fcmMessagingClient;
  private final FcmTokenRepository fcmTokenRepository;

  /**
   * 같은 내용 일괄 발송
   */
  public FcmBatchSendResult send(List<FcmPushTarget> pushTargets, FcmPushContent pushContent) {
    return send(pushTargets, pushTarget -> pushContent);
  }

  /**
   * 대상별 내용 일괄 발송 (회원마다 본문이 다른 안내 등)
   */
  public FcmBatchSendResult send(List<FcmPushTarget> pushTargets, Function<FcmPushTarget, FcmPushContent> contentByTarget) {
    FcmBatchSendResult totalResult = FcmBatchSendResult.empty();
    for (int chunkStartIndex = 0; chunkStartIndex < pushTargets.size(); chunkStartIndex += MAXIMUM_MESSAGES_PER_BATCH) {
      List<FcmPushTarget> chunkTargets = pushTargets.subList(chunkStartIndex,
          Math.min(chunkStartIndex + MAXIMUM_MESSAGES_PER_BATCH, pushTargets.size()));
      totalResult = totalResult.plus(sendChunk(chunkTargets, contentByTarget));
    }
    return totalResult;
  }

  // 묶음 1개: 메시지 생성 > sendEach > 성공·실패 집계 > 무효 토큰 삭제
  private FcmBatchSendResult sendChunk(List<FcmPushTarget> chunkTargets, Function<FcmPushTarget, FcmPushContent> contentByTarget) {
    Instant now = Instant.now();
    List<Message> messages = new ArrayList<>(chunkTargets.size());
    for (FcmPushTarget pushTarget : chunkTargets) {
      messages.add(FcmMessageFactory.createMessage(contentByTarget.apply(pushTarget), pushTarget.token(), now));
    }
    List<FcmSendOutcome> sendOutcomes = fcmMessagingClient.sendEach(messages);

    int successCount = 0;
    for (FcmSendOutcome sendOutcome : sendOutcomes) {
      if (sendOutcome.isSuccessful()) {
        successCount++;
      }
    }
    boolean hasSuccessInChunk = successCount > 0;
    List<String> invalidTokens = new ArrayList<>();
    for (int outcomeIndex = 0; outcomeIndex < sendOutcomes.size(); outcomeIndex++) {
      FcmSendOutcome sendOutcome = sendOutcomes.get(outcomeIndex);
      if (!sendOutcome.isSuccessful() && FcmInvalidTokenRule.isInvalidToken(sendOutcome.errorCode(), hasSuccessInChunk)) {
        invalidTokens.add(chunkTargets.get(outcomeIndex).token());
      }
    }
    int removedTokenCount = invalidTokens.isEmpty() ? 0 : fcmTokenRepository.deleteAllByTokenIn(invalidTokens);
    int failureCount = sendOutcomes.size() - successCount;
    if (failureCount > 0) {
      log.info("[fcm send] chunk done : messages={}, success={}, failure={}, removedTokens={}",
          messages.size(), successCount, failureCount, removedTokenCount);
    }
    return new FcmBatchSendResult(successCount, failureCount, removedTokenCount);
  }
}
