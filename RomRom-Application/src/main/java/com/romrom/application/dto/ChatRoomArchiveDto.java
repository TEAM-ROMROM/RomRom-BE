package com.romrom.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 채팅방 아카이브 직렬화 모델. 이미지는 imageUrls(URL 참조)만 포함하고 바이너리는 백업하지 않는다.
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatRoomArchiveDto {
  private UUID chatRoomId;
  private UUID tradeReceiverId;
  private UUID tradeSenderId;
  private LocalDateTime deletedAt;
  private LocalDateTime archivedAt;
  private List<MessageEntry> messages;

  @Getter @Builder @NoArgsConstructor @AllArgsConstructor
  public static class MessageEntry {
    private String chatMessageId;
    private UUID senderId;
    private UUID recipientId;
    private String content;
    private List<String> imageUrls;   // URL 참조만 (바이너리 미백업)
    private String type;
    private LocalDateTime createdDate;
  }
}
