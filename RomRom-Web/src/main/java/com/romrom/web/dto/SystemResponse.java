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

  // POST /api/app/version/check 응답
  @Schema(description = "강제 업데이트 필요 여부")
  private Boolean forceUpdate;

  @Schema(description = "권장 업데이트 여부")
  private Boolean recommendUpdate;

  @Schema(description = "앱 최신 버전", example = "1.4.41")
  private String latestVersion;

  @Schema(description = "스토어 URL (플랫폼에 맞는 URL)")
  private String storeUrl;

  // POST /api/admin/config/version 응답
  @Schema(description = "앱 최소 필수 버전 (강제 업데이트 기준)", example = "1.4.0")
  private String minVersion;

  @Schema(description = "iOS App Store URL")
  private String storeIos;

  @Schema(description = "Android Google Play URL")
  private String storeAndroid;
}
