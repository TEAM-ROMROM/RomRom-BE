package com.romrom.chat.service;

import com.romrom.chat.dto.ChatActionRecommendationPayload;
import com.romrom.chat.dto.ChatMessagePayload;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.stomp.properties.ChatRoutingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatWebSocketService {
  private final SimpMessagingTemplate template;
  private final ChatRoutingProperties chatRoutingProperties;

  public void sendReadEvent(ChatUserState payload) {
    String roomRoutingKey = "chat.read." + payload.getChatRoomId();
    String destination = "/exchange/" + chatRoutingProperties.getChatExchange() + "/" + roomRoutingKey;
    template.convertAndSend(destination, payload);
    log.debug("채팅 읽음 이벤트 브로커 송출 완료, destination: {}", destination);
  }

  public void sendToBroker(ChatMessage message, boolean isProfanityDetected) {
    // 메시지 브로커 전송
    ChatMessagePayload payload = ChatMessagePayload.from(message, isProfanityDetected);
    String roomRoutingKey = "chat.room." + payload.getChatRoomId();
    String destination = "/exchange/" + chatRoutingProperties.getChatExchange() + "/" + roomRoutingKey;

    // RabbitMQ 브로커에게 메시지 전달
    template.convertAndSend(destination, payload);
    log.debug("채팅 메시지 브로커 송출 완료, destination: {}", destination);
  }

  // 추천 이벤트는 채팅방 공용 브로드캐스트가 아니라 현재 사용자 1명에게만 보여줘야 해서
  // exchange 라우팅 대신 convertAndSendToUser + /queue 하위 경로를 사용한다.
  public void sendRecommendationEvent(String username, ChatActionRecommendationPayload payload) {
    String destination = "/queue/chat.recommend." + payload.getChatRoomId();
    template.convertAndSendToUser(username, destination, payload);
    log.debug("채팅 추천 이벤트 사용자 전송 완료, username={}, destination={}", username, destination);
  }
}
