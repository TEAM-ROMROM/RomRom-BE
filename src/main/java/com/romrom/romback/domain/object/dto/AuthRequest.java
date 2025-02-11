package com.romrom.romback.domain.object.dto;

import com.romrom.romback.domain.object.constant.SocialPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class AuthRequest {
  @Schema(description = "로그인 플랫폼 (KAKAO, GOOGLE 등)", defaultValue = "KAKAO")
  private SocialPlatform socialPlatform;

  @Schema(description = "소셜 로그인 시 제공되는 인증 토큰", defaultValue = "socialToken123")
  private String socialAuthToken;
}
