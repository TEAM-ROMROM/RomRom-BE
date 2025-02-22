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
          date = "2025.02.23",
          author = Author.SUHSAECHAN,
          issueNumber = 32,
          description = "member isFirstLogin Transient 추가"
      ),
      @ApiChangeLog(
          date = "2025.02.10",
          author = Author.SUHSAECHAN,
          issueNumber = 15,
          description = "엔드포인트 주소 대문자 삭제, signIn -> signin, JWT 화이트리스트 추가"
      ),
      @ApiChangeLog(
          date = "2025.02.10",
          author = Author.SUHSAECHAN,
          issueNumber = 15,
          description = "OAuth 관련 토큰 로직 생성"
      ),
      @ApiChangeLog(
          date = "2025.02.10",
          author = Author.SUHSAECHAN,
          issueNumber = 12,
          description = "기본 로그인 기능 구현"
      )
  })
  @Operation(
      summary = "소셜 로그인",
      description = """
      ## 인증(JWT): **불필요**
      
      ## 요청 파라미터 (AuthRequest)
      - **`socialPlatform`**: 로그인 플랫폼 (KAKAO, GOOGLE )
      - **`socialAuthToken`**: 소셜 로그인 시 제공되는 인증 토큰
      
      ## 반환값 (AuthResponse)
      - **`accessToken`**: 발급된 AccessToken
      - **`refreshToken`**: 발급된 RefreshToken
      - **`isFirstLogin`**: 최초 로그인 여부
      
      ## 에러코드
      - **`INVALID_SOCIAL_TOKEN`**: 유효하지 않은 소셜 인증 토큰입니다.
      - **`SOCIAL_AUTH_FAILED`**: 소셜 로그인 인증에 실패하였습니다.
      - **`MEMBER_NOT_FOUND`**: 회원 정보를 찾을 수 없습니다.
      """
  )
  ResponseEntity<AuthResponse> signIn(AuthRequest request);
}
