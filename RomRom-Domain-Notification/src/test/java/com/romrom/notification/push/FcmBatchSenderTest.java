package com.romrom.notification.push;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.romrom.common.constant.DeviceType;
import com.romrom.notification.repository.FcmTokenRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * FCM 일괄 발송 단위 테스트 (#874): 500건 묶음, 집계, 무효 토큰 오류 코드별 삭제
 */
class FcmBatchSenderTest {

  private static final FcmPushContent PUSH_CONTENT = new FcmPushContent("제목", "본문", null,
      Map.of("notificationType", "ANNOUNCEMENT"), FcmPushContent.DEFAULT_TIME_TO_LIVE, null);

  private FcmMessagingClient fcmMessagingClient;
  private FcmTokenRepository fcmTokenRepository;
  private FcmBatchSender fcmBatchSender;
  private final List<Integer> sentChunkSizes = new ArrayList<>();

  @BeforeEach
  void setUp() {
    fcmMessagingClient = mock(FcmMessagingClient.class);
    fcmTokenRepository = mock(FcmTokenRepository.class);
    fcmBatchSender = new FcmBatchSender(fcmMessagingClient, fcmTokenRepository);
    when(fcmTokenRepository.deleteAllByTokenIn(anyCollection()))
        .thenAnswer(invocation -> ((Collection<?>) invocation.getArgument(0)).size());
  }

  private static List<FcmPushTarget> createTargets(int targetCount) {
    return IntStream.range(0, targetCount)
        .mapToObj(targetIndex -> new FcmPushTarget("token-" + targetIndex, UUID.randomUUID(), DeviceType.ANDROID))
        .toList();
  }

  // 클라이언트: 묶음 크기 기록 + 결과 목록 그대로 반환
  private void stubClientOutcomes(List<FcmSendOutcome> sendOutcomes) {
    when(fcmMessagingClient.sendEach(anyList())).thenAnswer(invocation -> {
      List<Message> messages = invocation.getArgument(0);
      sentChunkSizes.add(messages.size());
      return sendOutcomes.subList(0, messages.size());
    });
  }

  @Test
  @DisplayName("1200건 > 500·500·200 세 묶음으로 발송, 성공 수 합산")
  void send_500건단위_묶음() {
    stubClientOutcomes(java.util.Collections.nCopies(500, FcmSendOutcome.success()));

    FcmBatchSendResult sendResult = fcmBatchSender.send(createTargets(1200), PUSH_CONTENT);

    Assertions.assertEquals(List.of(500, 500, 200), sentChunkSizes);
    Assertions.assertEquals(1200, sendResult.successCount());
    Assertions.assertEquals(0, sendResult.failureCount());
    verify(fcmTokenRepository, never()).deleteAllByTokenIn(anyCollection());
  }

  @Test
  @DisplayName("대상 없음 > 발송 호출 없음")
  void send_대상없음() {
    FcmBatchSendResult sendResult = fcmBatchSender.send(List.of(), PUSH_CONTENT);
    Assertions.assertEquals(FcmBatchSendResult.empty(), sendResult);
    verify(fcmMessagingClient, never()).sendEach(anyList());
  }

  @Test
  @DisplayName("UNREGISTERED·SENDER_ID_MISMATCH·INVALID_ARGUMENT(성공 섞임) 토큰만 삭제, 일시 오류는 유지")
  void send_무효토큰_삭제() {
    stubClientOutcomes(List.of(
        FcmSendOutcome.success(),
        FcmSendOutcome.failure(MessagingErrorCode.UNREGISTERED),
        FcmSendOutcome.failure(MessagingErrorCode.SENDER_ID_MISMATCH),
        FcmSendOutcome.failure(MessagingErrorCode.INVALID_ARGUMENT),
        FcmSendOutcome.failure(MessagingErrorCode.UNAVAILABLE),
        FcmSendOutcome.failure(MessagingErrorCode.QUOTA_EXCEEDED)));

    FcmBatchSendResult sendResult = fcmBatchSender.send(createTargets(6), PUSH_CONTENT);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<String>> deletedTokensCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(fcmTokenRepository).deleteAllByTokenIn(deletedTokensCaptor.capture());
    Assertions.assertEquals(List.of("token-1", "token-2", "token-3"), new ArrayList<>(deletedTokensCaptor.getValue()));
    Assertions.assertEquals(new FcmBatchSendResult(1, 5, 3), sendResult);
  }

  @Test
  @DisplayName("묶음 전체가 INVALID_ARGUMENT > 메시지 오류로 보고 토큰 삭제 안 함")
  void send_전체INVALID_ARGUMENT_삭제안함() {
    stubClientOutcomes(java.util.Collections.nCopies(3, FcmSendOutcome.failure(MessagingErrorCode.INVALID_ARGUMENT)));

    FcmBatchSendResult sendResult = fcmBatchSender.send(createTargets(3), PUSH_CONTENT);

    verify(fcmTokenRepository, never()).deleteAllByTokenIn(anyCollection());
    Assertions.assertEquals(new FcmBatchSendResult(0, 3, 0), sendResult);
  }

  @Test
  @DisplayName("무효 토큰 판정 규칙")
  void isInvalidToken_규칙() {
    Assertions.assertTrue(FcmInvalidTokenRule.isInvalidToken(MessagingErrorCode.UNREGISTERED, false));
    Assertions.assertTrue(FcmInvalidTokenRule.isInvalidToken(MessagingErrorCode.SENDER_ID_MISMATCH, false));
    Assertions.assertTrue(FcmInvalidTokenRule.isInvalidToken(MessagingErrorCode.INVALID_ARGUMENT, true));
    Assertions.assertFalse(FcmInvalidTokenRule.isInvalidToken(MessagingErrorCode.INVALID_ARGUMENT, false));
    Assertions.assertFalse(FcmInvalidTokenRule.isInvalidToken(MessagingErrorCode.INTERNAL, true));
    Assertions.assertFalse(FcmInvalidTokenRule.isInvalidToken(null, true));
  }
}
