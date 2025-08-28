package com.romrom.chat.stomp.event;

import com.romrom.chat.dto.ChatMessagePayload;
import com.romrom.chat.stomp.properties.ChatRoutingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageEventListener {

  private final SimpMessagingTemplate template;
  private final ChatRoutingProperties chatRoutingProperties;

  @EventListener
  public void handleChatMessageSentEvent(ChatMessagePayload payload) {

    String roomRoutingKey = "chat.room." + payload.getChatRoomId();
    String destination = "/exchange/" + chatRoutingProperties.getChatExchange() + "/" + roomRoutingKey;

    // RabbitMQ 브로커에게 메시지 전달
    template.convertAndSend(destination, payload);
    log.debug("채팅 메시지 브로커 송출 완료 (이벤트 리스너). destination: {}", destination);
  }
}