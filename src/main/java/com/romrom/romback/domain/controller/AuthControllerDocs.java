package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.constant.Author;
import com.romrom.romback.domain.object.dto.AuthRequest;
import com.romrom.romback.domain.object.dto.AuthResponse;
import com.romrom.romback.global.docs.ApiChangeLog;
import com.romrom.romback.global.docs.ApiChangeLogs;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;

public interface AuthControllerDocs {



  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.02.10",
          author = Author.SUHSAECHAN,
          issueNumber = 12,
          description = "로그인 API DOCS 개선"
      )
  })
  @Operation(
      summary = "로그인 (소셜 로그인)",
      description = """
          ## 인증(JWT): **불필요**
          
          ## 참고사항
          - 소셜 로그인 시 제공된 인증 토큰을 이용해 회원 정보를 확인 후 토큰을 발급합니다.
          
          ## 요청 파라미터 (AuthRequest)
          - **`socialPlatform`**: 로그인 플랫폼 (KAKAO, GOOGLE 등)
          - **`socialAuthToken`**: 소셜 로그인 시 제공되는 인증 토큰
          
          ## 반환값
          - **`accessToken`**, **`refreshToken`**
          
          ## 에러코드
          - **`USER_NOT_FOUND`**: 가입되지 않은 사용자입니다.
          """
  )
  ResponseEntity<AuthResponse> signIn(AuthRequest request);
}
