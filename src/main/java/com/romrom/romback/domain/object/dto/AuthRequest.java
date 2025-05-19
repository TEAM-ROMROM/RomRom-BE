package com.romrom.romback.domain.object.dto;

import com.romrom.romback.domain.object.constant.SocialPlatform;
import com.romrom.romback.domain.object.postgres.Member;
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
public class AuthRequest {

  private Member member;

  @Schema(description = "로그인 플랫폼 (KAKAO, GOOGLE 등)", defaultValue = "KAKAO")
  private SocialPlatform socialPlatform;

  @Schema(description = "소셜 로그인 후 반환된 이메일", defaultValue = "example@naver.com")
  private String email;

  @Schema(description = "소셜 로그인 후 반환된 닉네임", defaultValue = "nickname123")
  private String nickname;

  @Schema(description = "소셜 로그인 후 반환된 프로필 URL", defaultValue = "https://example.com")
  private String profileUrl;

  private String accessToken;

  private String refreshToken;

  @Schema(description = "서비스 이용약관 (필수)", defaultValue = "true", required = true)
  private boolean hasServiceTermAgreed;

  @Schema(description = "개인정보 수집 및 이용동의 (필수)", defaultValue = "true", required = true)
  private boolean hasPrivacyPolicyAgreed;

  @Schema(description = "위치정보 서비스 이용약관 (선택)", defaultValue = "true")
  private boolean hasLocationTermAgreed;

  @Schema(description = "마케팅 정보 수신동의 여부 (선택)", defaultValue = "true")
  private boolean hasMarketingInfoAgreed;

}
