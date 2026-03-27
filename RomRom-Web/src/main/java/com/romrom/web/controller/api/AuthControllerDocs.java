package com.romrom.web.controller.api;

import com.romrom.auth.dto.AuthRequest;
import com.romrom.auth.dto.AuthResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.dto.LoginRequest;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AuthControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.03.27", author = Author.SUHSAECHAN, issueNumber = 605, description = "동일 이메일 다른 소셜 플랫폼 로그인 시 409 에러 응답 추가"),
      @ApiChangeLog(date = "2026.03.05", author = Author.WISEUNGJAE, issueNumber = 561, description = "Firebase Authentication 기반 통합 로그인으로 전환, 기존 /sign-in 제거"),
      @ApiChangeLog(date = "2026.01.13", author = Author.WISEUNGJAE, issueNumber = 446, description = "회원가입 시 알림수신여부 false로 초기화"),
      @ApiChangeLog(date = "2025.04.07", author = Author.WISEUNGJAE, issueNumber = 90, description = "소셜 로그인 시 nickname 제거 후 랜덤 닉네임 지정"),
      @ApiChangeLog(date = "2025.02.23", author = Author.SUHSAECHAN, issueNumber = 32, description = "member isFirstLogin Transient 추가"),
      @ApiChangeLog(date = "2025.02.14", author = Author.BAEKJIHOON, issueNumber = 31, description = "소셜로그인 방식 변경에따른 로그인 api 파라미터 값 수정"),
      @ApiChangeLog(date = "2025.02.10", author = Author.SUHSAECHAN, issueNumber = 15, description = "엔드포인트 주소 대문자 삭제, signIn -> signin, JWT 화이트리스트 추가"),
      @ApiChangeLog(date = "2025.02.10", author = Author.SUHSAECHAN, issueNumber = 15, description = "OAuth 관련 토큰 로직 생성"),
      @ApiChangeLog(date = "2025.02.10", author = Author.SUHSAECHAN, issueNumber = 12, description = "기본 로그인 기능 구현"),
  })
  @Operation(
      summary = "Firebase 통합 로그인",
      description = """
      ## 인증(JWT): **불필요**

      ## 요청 파라미터 (LoginRequest)
      - **`firebaseIdToken`**: Flutter Firebase Authentication에서 발급된 ID Token
      - **`providerId`**: 소셜 로그인 제공자 ID (google.com, oidc.kakao)
      - **`profile.email`**: 소셜 로그인 이메일 (서버에서 미사용, Firebase 토큰에서 추출)
      - **`profile.displayName`**: 소셜 로그인 닉네임 (서버에서 미사용, 랜덤 생성)
      - **`profile.photoUrl`**: 소셜 로그인 프로필 이미지 URL
      - **`client.platform`**: 플랫폼 (ios, android)
      - **`client.appVersion`**: 앱 버전
      - **`client.deviceModel`**: 기기 모델명
      - **`client.locale`**: 로케일

      ## 반환값 (AuthResponse)
      - **`accessToken`**: 발급된 AccessToken
      - **`refreshToken`**: 발급된 RefreshToken
      - **`isFirstLogin`**: 최초 로그인 여부
      - **`isFirstItemPosted`**: 첫 물품 등록 여부
      - **`isItemCategorySaved`**: 선호 카테고리 저장 여부
      - **`isMemberLocationSaved`**: 위치 저장 여부
      - **`isMarketingInfoAgreed`**: 마케팅 정보 수신 동의 여부
      - **`isRequiredTermsAgreed`**: 필수 이용약관 동의 여부

      ## 특이사항
      - email은 Firebase 토큰에서 서버가 직접 추출합니다 (클라이언트 전송값 무시)
      - 닉네임은 서버에서 랜덤으로 생성합니다

      ## 에러코드
      - **`INVALID_FIREBASE_TOKEN`**: 유효하지 않은 Firebase 인증 토큰입니다.
      - **`EXPIRED_FIREBASE_TOKEN`**: 만료된 Firebase 인증 토큰입니다.
      - **`INVALID_SOCIAL_PLATFORM`**: 지원하지 않는 소셜 로그인 제공자입니다.
      - **`EMAIL_ALREADY_REGISTERED`**: 이미 다른 소셜 플랫폼으로 가입된 이메일입니다. (409 + EmailAlreadyRegisteredResponse: errorCode, registeredSocialPlatform)
      """
  )
  ResponseEntity<AuthResponse> login(LoginRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.02.15", author = Author.BAEKJIHOON, issueNumber = 30, description = "엑세스 토큰 재발급 init"),
  })
  @Operation(
      summary = "accessToken 재발급",
      description = """
      ## 인증(JWT): **불필요**
      
      ## 요청 파라미터 (AuthRequest)
      - **`refreshToken`**: 리프레시 토큰
      
      
      ## 반환값 (AuthResponse)
      - **`accessToken`**: 재발급된 AccessToken (refreshToken은 반환되지 않음)
      - **`isFirstLogin`**: 최초 로그인 여부
      - **`isFirstItemPosted`**: 첫 물품 등록 여부
      - **`isItemCategorySaved`**: 선호 카테고리 저장 여부
      - **`isMemberLocationSaved`**: 위치 저장 여부
      - **`isMarketingInfoAgreed`**: 마케팅 정보 수신 동의 여부
      - **`isRequiredTermsAgreed`**: 필수 이용약관 동의 여부
      
      ## 에러코드
      - **`REFRESH_TOKEN_NOT_FOUND`**: 리프레시 토큰을 찾을 수 없습니다.
      - **`INVALID_REFRESH_TOKEN`**: 유효하지 않은 리프레시 토큰입니다.
      - **`EXPIRED_REFRESH_TOKEN`**: 만료된 리프레시 토큰입니다.
      - **`MEMBER_NOT_FOUND`**: 회원 정보를 찾을 수 없습니다.
      - **`SUSPENDED_MEMBER`**: 정지된 회원입니다. (403 + SuspendedMemberResponse)
      """
  )
  ResponseEntity<AuthResponse> reissue(AuthRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.02.17", author = Author.BAEKJIHOON, issueNumber = 30, description = "로그아웃 시 엑세스 토큰 블랙리스트 & 리프레시토큰 삭제"),
  })
  @Operation(
      summary = "로그아웃",
      description = """
      ## 인증(JWT): **필요**
      
      ## 요청 파라미터 (AuthRequest)
      - **`accessToken`**: 엑세스 토큰
      - **`refreshToken`**: 리프레시 토큰
      
      ## 반환값
      - 성공 시 상태코드 200 (OK)와 빈 응답 본문
      
      ## 동작 설명
      - 액세스 토큰을 블랙리스트에 등록하여 무효화 처리
      - Redis에 저장된 리프레시 토큰 삭제
      
      ## 에러코드
      - **`INVALID_TOKEN`**: 유효하지 않은 토큰입니다.
      - **`UNAUTHORIZED`**: 인증이 필요한 요청입니다.
      """
  )
  ResponseEntity<Void> logout(
      CustomUserDetails customUserDetails,
      AuthRequest request);
}
