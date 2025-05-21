package com.romrom.romback.domain.object.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AuthResponse {
  @Schema(description = "발급된 AccessToken")
  private String accessToken;

  @Schema(description = "발급된 RefreshToken")
  private String refreshToken;

  private Boolean isFirstLogin; // 첫 로그인 여부

  private Boolean isFirstItemPosted; // 첫 물품 등록 여부

  private Boolean isItemCategorySaved; // 선호 카테고리 저장 여부

  private Boolean isMarketingInfoAgreed; // 마케팅 동의 여부

  private Boolean isRequiredTermsAgreed;  // 필수 이용약관 동의 여부
}
