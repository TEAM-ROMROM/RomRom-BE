package com.romrom.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.romrom.common.constant.DeviceType;
import com.romrom.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
public class NotificationRequest {

  @Schema(hidden = true, description = "회원")
  @JsonIgnore
  private Member member;

  @NotBlank(message = "FCM 토큰을 입력해주세요")
  @ToString.Exclude
  private String fcmToken; // FCM 토큰

  private DeviceType deviceType;

  private String title;

  private String body;

  @Schema(description = "열람한 푸시 캠페인 ID (FCM data campaignId, #874)")
  private String campaignId;
}