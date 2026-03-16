package com.romrom.chat.entity.mongo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.romrom.common.entity.mongo.BaseMongoEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document
@CompoundIndex(def = "{'chatRoomId': 1, 'memberId': 1}", unique = true)
public class ChatUserState extends BaseMongoEntity {
  @Id
  private String chatUserStateId;
  private UUID chatRoomId;
  private UUID memberId;
  private LocalDateTime leftAt;             // 채팅방 나간 시점 ( = 마지막으로 읽은 시점 = 커서) (현재 채팅방에 접속 중이면 null)
  private LocalDateTime removedAt;          // null이면 정상, 값이 있으면 나에게만 삭제된 방

  /**
   * 사용자가 현재 채팅방 화면에 머물고 있는지 여부
   * (leftAt이 null이면 현재 접속 중으로 간주)
   */
  @JsonProperty("isPresent")
  public boolean isPresent() {
    if(this.getLeftAt() == null) {
      return true;
    }
    return false;
  }

  public void removeRoom() {
    this.removedAt = LocalDateTime.now();
  }

  public boolean isDeleted() {
    if (this.removedAt == null) {
      return false;
    }
    return true;
  }
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
        .removedAt(null)
        .build();
  }
}
