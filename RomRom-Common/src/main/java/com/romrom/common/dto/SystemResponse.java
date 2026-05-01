package com.romrom.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemResponse {

  @Schema(description = "앱 최소 필수 버전 (이 버전 미만이면 강제 업데이트)", example = "1.9.0")
  private String minimumVersion;

  @Schema(description = "앱 최신 버전", example = "1.9.67")
  private String latestVersion;

  @Schema(description = "Android Google Play URL", example = "https://play.google.com/store/apps/details?id=com.alom.romrom&hl=ko")
  private String androidStoreUrl;

  @Schema(description = "iOS App Store URL", example = "https://apps.apple.com/kr/app/id...")
  private String iosStoreUrl;

  @Schema(description = "서버 점검 모드 활성화 여부", example = "false")
  private Boolean maintenanceEnabled;

  @Schema(description = "점검 안내 메시지", example = "서버 점검 중입니다. 불편을 드려 죄송합니다.")
  private String maintenanceMessage;

  @Schema(description = "점검 예상 종료 시간 (ISO 8601, 없으면 null)", example = "2026-05-02T15:00:00")
  private String maintenanceEndTime;
}
