package com.romrom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginProfile {

  @Schema(description = "소셜 로그인 이메일", example = "example@gmail.com")
  private String email;

  @Schema(description = "소셜 로그인 닉네임", example = "홍길동")
  private String displayName;

  @Schema(description = "소셜 로그인 프로필 이미지 URL", example = "https://lh3.googleusercontent.com/...")
  private String photoUrl;
}
