package com.romrom.romback.domain.object.dto;

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
  @Schema(description = "발급된 AccessToken")
  private String accessToken;

  @Schema(description = "발급된 RefreshToken")
  private String refreshToken;
}
