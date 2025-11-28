package com.romrom.chat.entity.mongo;

import com.romrom.common.entity.mongo.BaseMongoEntity;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document
// TODO : unique = true의 '"' 를 }로 옮긴 후, db migration 필요 - db.chatUserState.dropIndex("chatRoomId_1_memberId_1");
@CompoundIndex(def = "{'chatRoomId': 1, 'memberId': 1},  unique = true")
public class ChatUserState extends BaseMongoEntity {
  @Id
  private String chatUserStateId;
  private UUID chatRoomId;
  private UUID memberId;
  private LocalDateTime leftAt;             // 채팅방 나간 시점 ( = 마지막으로 읽은 시점 = 커서) (현재 채팅방에 접속 중이면 null)

  public void enterChatRoom() {
    this.leftAt = null;
  }

  public void leaveChatRoom() {
    this.leftAt = LocalDateTime.now();
  }

  public static ChatUserState create(UUID chatRoomId, UUID memberId) {
    return ChatUserState.builder()
        .chatRoomId(chatRoomId)
        .memberId(memberId)
        .leftAt(LocalDateTime.now())      // 처음 생성 시에는 채팅방에 접속하지 않은 상태로 생성
        .build();
  }
}
