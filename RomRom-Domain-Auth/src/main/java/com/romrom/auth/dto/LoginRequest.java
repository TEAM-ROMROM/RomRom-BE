package com.romrom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class LoginRequest {

  @Schema(description = "Firebase 인증 후 발급된 ID Token", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6I....")
  private String firebaseIdToken;

  @Schema(description = "소셜 로그인 제공자 ID (google.com, oidc.kakao)", example = "google.com")
  private String providerId;

  @Schema(description = "소셜 로그인 프로필 정보")
  private LoginProfile profile;

  @Schema(description = "클라이언트 환경 정보")
  private LoginClient client;

  @ToString
  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LoginProfile {

    @Schema(description = "소셜 로그인 이메일", example = "example@gmail.com")
    private String email;

    @Schema(description = "소셜 로그인 닉네임", example = "홍길동")
    private String displayName;

    @Schema(description = "소셜 로그인 프로필 이미지 URL", example = "https://lh3.googleusercontent.com/...")
    private String photoUrl;
  }

  @ToString
  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LoginClient {

    @Schema(description = "플랫폼 (ios, android)", example = "ios")
    private String platform;

    @Schema(description = "앱 버전", example = "1.0.0")
    private String appVersion;

    @Schema(description = "기기 모델", example = "iPhone15,3")
    private String deviceModel;

    @Schema(description = "로케일", example = "ko-KR")
    private String locale;
  }
}
