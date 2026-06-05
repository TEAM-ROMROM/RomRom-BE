package com.romrom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KakaoFirebaseTokenRequest {

  @Schema(description = "카카오 SDK에서 발급된 accessToken", example = "KXFyGq7...")
  private String accessToken;

  @Schema(description = "카카오 계정 email (기존 카카오 회원 매칭 보조용, nullable)", example = "user@kakao.com")
  private String email;
}
