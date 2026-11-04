package com.romrom.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.postgres.ChatRoom;
import lombok.*;
import org.springframework.data.domain.Slice;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ChatRoomResponse {
  private Boolean isOpponentDeleted;
  private ChatRoom chatRoom;
  private Slice<ChatMessage> messages;
  private Slice<ChatRoomDetailDto> chatRoomDetailDtoPage;
  private ChatUserState opponentState;
  private ChatActionRecommendationPayload latestRecommendation;
  // 내 이 방 알림 끄기 여부 (메시지 조회·알림 설정 변경 응답)
  @JsonProperty("isNotificationMuted")
  private Boolean isNotificationMuted;
  // 변경된 메시지 1건 (삭제·약속 제안·수락·취소 응답, 회원 조회용 마스킹 적용)
  private ChatMessage chatMessage;
}
