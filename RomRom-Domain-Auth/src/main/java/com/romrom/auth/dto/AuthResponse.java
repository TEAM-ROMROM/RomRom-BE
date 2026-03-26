package com.romrom.auth.dto;

import com.romrom.common.constant.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
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

  private Boolean isMemberLocationSaved; // 위치 저장 여부
  
  private Boolean isMarketingInfoAgreed; // 마케팅 정보 수신 동의 여부

  private Boolean isRequiredTermsAgreed;  // 필수 이용약관 동의 여부

  private AccountStatus accountStatus; // 계정 상태

  private String suspendReason; // 제재 사유 (정지 계정만)

  private LocalDateTime suspendedUntil; // 해제 예정 일시 (정지 계정만)
}
