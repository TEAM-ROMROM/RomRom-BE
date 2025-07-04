package com.romrom.application.service;

import com.romrom.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class TestResponse {

  @Schema(description = "저장된 회원")
  private Member member;

  @Schema(description = "발급된 AccessToken")
  private String accessToken;

  @Schema(description = "발급된 RefreshToken")
  private String refreshToken;

  @Schema(description = "신규 회원 여부")
  private Boolean isFirstLogin;

  @Schema(description = "첫 물품 등록 여부")
  private Boolean isFirstItemPosted;
}
