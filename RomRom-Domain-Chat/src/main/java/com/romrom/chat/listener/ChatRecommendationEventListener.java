package com.romrom.chat.listener;

import com.romrom.chat.event.ChatRecommendationRequestedEvent;
import com.romrom.chat.service.ChatActionRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatRecommendationEventListener {

  private final ChatActionRecommendationService chatActionRecommendationService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(ChatRecommendationRequestedEvent event) {
    try {
      chatActionRecommendationService.dispatchRealtimeRecommendations(event);
    } catch (Exception e) {
      log.error("채팅 추천 이벤트 처리 실패: roomId={}, messageId={}, error={}",
          event.chatRoomId(), event.basedOnMessageId(), e.getMessage(), e);
    }
  }
}
