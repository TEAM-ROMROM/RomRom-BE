package com.romrom.web.dto;

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
}
