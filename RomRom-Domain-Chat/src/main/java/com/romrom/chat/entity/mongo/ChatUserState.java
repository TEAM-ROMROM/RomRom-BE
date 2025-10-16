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
  private String lastReadMessageId;

  private Instant lastReadAt;
  private Instant leftAt;

  public void updateLastReadMessage(String lastReadMessageId) {
    this.lastReadMessageId = lastReadMessageId;
  }

  public void enterChatRoom() {
    this.lastReadMessageId = null;
    this.lastReadAt = Instant.now();
    this.leftAt = null;
  }

  public void leaveChatRoom(String lastReadMessageId) {
    this.lastReadMessageId = lastReadMessageId;
    this.lastReadAt = Instant.now();
    this.leftAt = Instant.now();
  }

  public static ChatUserState fromRoomIdAndMemberId(UUID chatRoomId, UUID memberId) {
    return ChatUserState.builder()
        .chatRoomId(chatRoomId)
        .memberId(memberId)
        .lastReadMessageId(null)
        .lastReadAt(null)
        .leftAt(null)
        .build();
  }
}
