package com.romrom.notification.dto;

import com.romrom.common.constant.DeviceType;
import com.romrom.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
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

  private Member member;

  @NotBlank(message = "FCM 토큰을 입력해주세요")
  @Schema(defaultValue = "chM0thKFkDxdhMm5WAMMwQ:APA91bGKs8nN4A_bPClciFu88Z2bgN9-gvPKsopGsxPcKS2K86Gu9JcZi0HPHgcwpONymKpTiMPE4ztmIt0qXOl9gKUom13Aze80CkvPE6JwEuAgxJDRGtg")
  private String fcmToken; // FCM 토큰

  private DeviceType deviceType;

  private String title;

  private String body;

  private List<UUID> memberIdList;
}