package com.romrom.chat.entity.mongo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 약속 카드 내용: 일시, 장소, 좌표(선택), 상태 (ChatMessage 내장 문서)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatAppointment {

  // 장소 이름 최대 길이
  public static final int PLACE_NAME_MAX_LENGTH = 50;

  private LocalDateTime appointmentAt;
  private String placeName;
  private Double latitude;
  private Double longitude;
  private ChatAppointmentStatus status;
  // 마지막 상태 변경 회원 (제안 시 제안자)
  private UUID statusChangedMemberId;
  // 리마인드 발송 선점 시각 (null이면 미발송, 회원 응답 비노출)
  @JsonIgnore
  private LocalDateTime reminderSentAt;

  public static ChatAppointment propose(LocalDateTime appointmentAt, String placeName, Double latitude,
                                        Double longitude, UUID proposerMemberId) {
    return ChatAppointment.builder()
        .appointmentAt(appointmentAt)
        .placeName(placeName)
        .latitude(latitude)
        .longitude(longitude)
        .status(ChatAppointmentStatus.PROPOSED)
        .statusChangedMemberId(proposerMemberId)
        .build();
  }

  public void changeStatus(ChatAppointmentStatus newStatus, UUID changedMemberId) {
    this.status = newStatus;
    this.statusChangedMemberId = changedMemberId;
  }
}
