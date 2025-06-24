package com.romrom.web.controller;

import com.romrom.auth.dto.AuthRequest;
import com.romrom.auth.dto.AuthResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AuthControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.04.07",
          author = Author.WISEUNGJAE,
          issueNumber = 90,
          description = "소셜 로그인 시 nickname 제거 후 랜덤 닉네임 지정"
      ),
      @ApiChangeLog(
          date = "2025.02.23",
          author = Author.SUHSAECHAN,
          issueNumber = 32,
          description = "member isFirstLogin Transient 추가"
      ),
      @ApiChangeLog(
          date = "2025.02.14",
          author = Author.BAEKJIHOON,
          issueNumber = 31,
          description = "소셜로그인 방식 변경에따른 로그인 api 파라미터 값 수정"
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
      - **`nickname`**: 사용자 닉네임
      - **`profileUrl`**: 사용자 프로필 url
      - **`email`**: 사용자 이메일
      
      
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

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.02.15",
          author = Author.BAEKJIHOON,
          issueNumber = 30,
          description = "엑세스 토큰 재발급 init"
      )
  })
  @Operation(
      summary = "accessToken 재발급",
      description = """
      ## 인증(JWT): **불필요**
      
      ## 요청 파라미터 (AuthRequest)
      - **`refreshToken`**: 리프레시 토큰
      
      
      ## 반환값 (AuthResponse)
      - **`accessToken`**: 재발급된 AccessToken
      
      ## 에러코드
      - **`REFRESH_TOKEN_NOT_FOUND`**: 리프레시 토큰을 찾을 수 없습니다.
      - **`INVALID_REFRESH_TOKEN`**: 유효하지 않은 리프레시 토큰입니다.
      """
  )
  ResponseEntity<AuthResponse> reissue(AuthRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.02.17",
          author = Author.BAEKJIHOON,
          issueNumber = 30,
          description = "로그아웃 시 엑세스 토큰 블랙리스트 & 리프레시토큰 삭제"
      )
  })
  @Operation(
      summary = "로그아웃",
      description = """
      ## 인증(JWT): **필요**
      
      ## 요청 파라미터 (AuthRequest)
      - **`accessToken`**: 엑세스 토큰
      - **`refreshToken`**: 리프레시 토큰
      
      ## 반환값 (AuthResponse)
      - **`없읍`**
      """
  )
  ResponseEntity<Void> logout(
      CustomUserDetails customUserDetails,
      AuthRequest request);
}
