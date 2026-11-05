package com.romrom.notification.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Firebase Admin sendEach 호출 (최대 500건) > 결과 변환, 요청 전체 실패 > 전 건 실패 처리
 */
@Component
@Slf4j
public class FirebaseFcmMessagingClient implements FcmMessagingClient {

  @Override
  public List<FcmSendOutcome> sendEach(List<Message> messages) {
    if (messages.isEmpty()) {
      return List.of();
    }
    try {
      BatchResponse batchResponse = FirebaseMessaging.getInstance().sendEach(messages);
      List<FcmSendOutcome> sendOutcomes = new ArrayList<>(messages.size());
      for (SendResponse sendResponse : batchResponse.getResponses()) {
        sendOutcomes.add(sendResponse.isSuccessful()
            ? FcmSendOutcome.success()
            : FcmSendOutcome.failure(sendResponse.getException() == null ? null
                : sendResponse.getException().getMessagingErrorCode()));
      }
      return sendOutcomes;
    } catch (FirebaseMessagingException batchException) {
      log.error("[fcm send] batch failed : messages={}, errorCode={}, error={}", messages.size(),
          batchException.getMessagingErrorCode(), batchException.getMessage());
      return Collections.nCopies(messages.size(), FcmSendOutcome.failure(batchException.getMessagingErrorCode()));
    } catch (RuntimeException unexpectedException) {
      // Firebase 미초기화 등: 전 건 실패 (토큰 삭제 없음)
      log.error("[fcm send] batch failed : messages={}, error={}", messages.size(), unexpectedException.getMessage());
      return Collections.nCopies(messages.size(), FcmSendOutcome.failure(null));
    }
  }
}
