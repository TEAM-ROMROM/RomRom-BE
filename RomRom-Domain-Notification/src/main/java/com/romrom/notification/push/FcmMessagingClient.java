package com.romrom.notification.push;

import com.google.firebase.messaging.Message;
import java.util.List;

/**
 * FCM 발송 경계: 메시지 목록 > 같은 순서의 결과 목록 (테스트에서 대체)
 */
public interface FcmMessagingClient {

  List<FcmSendOutcome> sendEach(List<Message> messages);
}
