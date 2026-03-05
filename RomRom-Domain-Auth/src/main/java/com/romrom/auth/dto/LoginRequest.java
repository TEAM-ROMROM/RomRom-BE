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
}
