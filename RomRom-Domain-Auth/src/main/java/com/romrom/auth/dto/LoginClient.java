package com.romrom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginClient {

  @Schema(description = "플랫폼 (ios, android)", example = "ios")
  private String platform;

  @Schema(description = "앱 버전", example = "1.0.0")
  private String appVersion;

  @Schema(description = "기기 모델", example = "iPhone15,3")
  private String deviceModel;

  @Schema(description = "로케일", example = "ko-KR")
  private String locale;
}
