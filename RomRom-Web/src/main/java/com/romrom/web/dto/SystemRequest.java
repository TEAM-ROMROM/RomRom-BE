package com.romrom.web.dto;

import com.romrom.common.constant.DeviceType;
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
public class SystemRequest {

  // POST /api/app/version/check
  @Schema(description = "앱 버전", example = "1.3.0")
  private String appVersion;

  @Schema(description = "플랫폼", example = "IOS")
  private DeviceType platform;

  // POST /api/admin/config/version
  @Schema(description = "앱 최소 필수 버전 (강제 업데이트 기준)", example = "1.4.0")
  private String minVersion;

  @Schema(description = "앱 최신 버전 (권장 업데이트 기준)", example = "1.4.41")
  private String latestVersion;

  @Schema(description = "iOS App Store URL", example = "https://apps.apple.com/...")
  private String storeIos;

  @Schema(description = "Android Google Play URL", example = "https://play.google.com/...")
  private String storeAndroid;
}
