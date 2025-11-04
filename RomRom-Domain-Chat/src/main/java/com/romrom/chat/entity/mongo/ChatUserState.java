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
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document
@CompoundIndex(def = "{'chatRoomId': 1, 'memberId': 1},  unique = true")
public class ChatUserState extends BaseMongoEntity {
  @Id
  private String chatUserStateId;

  @Column(nullable = false)
  private UUID chatRoomId;

  @Column(nullable = false)
  private UUID memberId;

  @Column(nullable = true)
  private Instant leftAt;             // 채팅방 나간 시점 ( = 마지막으로 읽은 시점 = 커서) (현재 채팅방에 접속 중이면 null)

  public void enterChatRoom() {
    this.leftAt = null;
  }

  public void leaveChatRoom() {
    this.leftAt = Instant.now();
  }

  public static ChatUserState create(UUID chatRoomId, UUID memberId) {
    return ChatUserState.builder()
        .chatRoomId(chatRoomId)
        .memberId(memberId)
        .leftAt(Instant.now())      // 처음 생성 시에는 채팅방에 접속하지 않은 상태로 생성
        .build();
  }
}
