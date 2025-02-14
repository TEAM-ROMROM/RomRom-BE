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

  @Schema(description = "소셜 로그인 후 반환된 이메일", defaultValue = "example@naver.com")
  private String email;

  @Schema(description = "소셜 로그인 후 반환된 닉네임", defaultValue = "nickname123")
  private String nickname;

  @Schema(description = "소셜 로그인 후 반환된 프로필 URL", defaultValue = "https://example.com")
  private String profileUrl;
}
