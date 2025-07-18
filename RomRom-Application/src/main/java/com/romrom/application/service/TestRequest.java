package com.romrom.application.service;

import com.romrom.common.constant.SocialPlatform;
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
public class TestRequest {
  @Schema(description = "TEST 로그인 플랫폼", defaultValue = "KAKAO")
  private SocialPlatform socialPlatform;

  @Schema(description = "TEST 이메일", defaultValue = "testEmail@test.com")
  private String email;

  @Schema(description = "TEST 닉네임", defaultValue = "testNickname")
  private String nickname;
}
