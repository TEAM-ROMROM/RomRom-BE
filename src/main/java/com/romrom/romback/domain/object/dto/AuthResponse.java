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

  private Boolean isFirstLogin;
}
