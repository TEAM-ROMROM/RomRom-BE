package com.romrom.romback.domain.object.dto;

import com.romrom.romback.domain.object.constant.SocialPlatform;
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
  @Schema(description = "원하는 가입 로그인 플랫폼 (KAKAO, GOOGLE 등)", defaultValue = "KAKAO")
  private SocialPlatform socialPlatform;

  @Schema(description = "원하는 가입 email")
  private String email;

  @Schema(description = "원하는 가입 email")
  private String nickname;
}
