package com.romrom.romback.domain.object.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
public class AuthRequest {

  @Schema(defaultValue = "example123")
  private String username;

  @Schema(defaultValue = "pw12345")
  private String password; // 비밀번호

  @Schema(defaultValue = "nickname123")
  private String nickname; // 닉네임
}
