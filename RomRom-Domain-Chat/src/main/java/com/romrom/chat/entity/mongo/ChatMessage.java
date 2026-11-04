package com.romrom.chat.entity.mongo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.romrom.chat.dto.ChatMessageRequest;
import com.romrom.common.entity.mongo.BaseMongoEntity;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document
@CompoundIndex(def = "{'chatRoomId': 1, 'createdDate': -1}")
public class ChatMessage extends BaseMongoEntity {
  @Id
  private String chatMessageId;
  private UUID chatRoomId;
  private UUID senderId;
  private UUID recipientId;
  private String content;
  private Double latitude;
  private Double longitude;
  @Builder.Default
  private List<String> imageUrls = new ArrayList<>();
  private MessageType type;

  // 관리자 숨김 시각 (null이면 노출, 값이 있으면 회원 조회 시 마스킹, 원본은 보존)
  private LocalDateTime hiddenAt;
  // 숨김 처리 관리자
  private UUID hiddenByAdminId;

  // 보낸 사람 삭제 시각 (null이면 노출, 값이 있으면 회원 조회 시 안내 문구, 원본은 보존)
  private LocalDateTime deletedAt;

  // 약속 카드 (type은 옛 앱 호환을 위해 TEXT 유지, 새 앱만 이 필드로 카드 표시)
  private ChatAppointment appointment;

  // 숨김 메시지가 회원에게 보이는 문구
  public static final String HIDDEN_MESSAGE_PLACEHOLDER = "관리자에 의해 가려진 메시지입니다.";
  // 삭제 메시지가 회원에게 보이는 문구
  public static final String DELETED_MESSAGE_PLACEHOLDER = "삭제된 메시지입니다.";

  @JsonProperty("isHidden")
  public boolean isHidden() {
    return hiddenAt != null;
  }

  public void hideByAdmin(UUID adminId) {
    this.hiddenAt = LocalDateTime.now();
    this.hiddenByAdminId = adminId;
  }

  public void unhide() {
    this.hiddenAt = null;
    this.hiddenByAdminId = null;
  }

  @JsonProperty("isDeleted")
  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void markDeleted() {
    this.deletedAt = LocalDateTime.now();
  }

  public boolean hasAppointment() {
    return appointment != null;
  }

  public void changeContent(String newContent) {
    this.content = newContent;
  }

  /**
   * 회원 조회 응답용 마스킹: 숨김 > 숨김 문구, 삭제 > 삭제 문구 (조회 전용 인스턴스에만 사용, 저장 금지)
   */
  public void maskForMemberView() {
    maskIfHidden();
    maskIfDeleted();
  }

  /**
   * 삭제 메시지 마스킹: 본문 > 안내 문구, type > TEXT (옛 앱은 글자로 표시), 사진·위치·약속 제거
   */
  private void maskIfDeleted() {
    if (!isDeleted() || isHidden()) {
      return;
    }
    this.content = DELETED_MESSAGE_PLACEHOLDER;
    this.type = MessageType.TEXT;
    this.imageUrls = new ArrayList<>();
    this.latitude = null;
    this.longitude = null;
    this.appointment = null;
  }

  /**
   * 회원 조회 응답용 마스킹: 숨김 메시지면 본문 > 안내 문구, 이미지·위치 제거 (조회 전용 인스턴스에만 사용, 저장 금지)
   */
  public void maskIfHidden() {
    if (!isHidden()) {
      return;
    }
    this.content = HIDDEN_MESSAGE_PLACEHOLDER;
    this.imageUrls = new ArrayList<>();
    this.latitude = null;
    this.longitude = null;
    this.appointment = null;
  }

  public static ChatMessage fromChatMessageRequest(ChatMessageRequest request, UUID senderId, UUID recipientId) {
    return ChatMessage.builder()
        .chatRoomId(request.getChatRoomId())
        .senderId(senderId)
        .recipientId(recipientId)
        .content(request.getContent())
        .latitude(request.getLatitude())
        .longitude(request.getLongitude())
        .type(request.getType())
        .imageUrls(request.getImageUrls() != null ? request.getImageUrls() : new ArrayList<>()) // null 방어 코드 추가
        .build();
  }
}
