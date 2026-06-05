package com.romrom.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KakaoFirebaseTokenResponse {

  @Schema(description = "Firebase signInWithCustomToken()에 사용할 Custom Token")
  private String customToken;
}
