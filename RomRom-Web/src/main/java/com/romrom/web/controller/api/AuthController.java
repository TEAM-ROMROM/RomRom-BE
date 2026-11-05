package com.romrom.web.controller.api;

import com.romrom.auth.dto.AuthRequest;
import com.romrom.auth.dto.AuthResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.dto.KakaoFirebaseTokenRequest;
import com.romrom.auth.dto.KakaoFirebaseTokenResponse;
import com.romrom.auth.dto.LoginRequest;
import com.romrom.auth.service.AuthService;
import com.romrom.auth.service.KakaoAuthService;
import com.romrom.notification.service.FcmTokenService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "인증 API",
    description = "회원 인증(Firebase 소셜 로그인) 관련 API 제공"
)
@RequestMapping("/api/auth")
// @LogMonitor 미사용: 요청·응답 본문에 토큰이 있어 INFO 로그에 원문이 남음 (#813)
public class AuthController implements AuthControllerDocs {
  private final AuthService authService;
  private final KakaoAuthService kakaoAuthService;
  private final FcmTokenService fcmTokenService;

  @Override
  @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  @Override
  @PostMapping(value = "/reissue", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AuthResponse> reissue(@ModelAttribute AuthRequest request) {
    return ResponseEntity.ok(authService.reissue(request));
  }

  @Override
  @PostMapping(value = "/logout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute AuthRequest request) {
    request.setMember(customUserDetails.getMember());
    authService.logout(request);
    deleteLogoutDeviceToken(request);
    return ResponseEntity.ok().build();
  }

  // 로그아웃 기기 FCM 토큰 삭제 (미전달 옛 앱 > 생략, 실패해도 로그아웃 성공, #874)
  private void deleteLogoutDeviceToken(AuthRequest request) {
    try {
      fcmTokenService.deleteTokenOfMember(request.getMember().getMemberId(), request.getFcmToken());
    } catch (RuntimeException tokenDeleteException) {
      log.warn("[auth logout] fcm token delete failed : memberId={}, error={}", request.getMember().getMemberId(),
          tokenDeleteException.getMessage());
    }
  }

  @Override
  @PostMapping(value = "/kakao/firebase-token", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<KakaoFirebaseTokenResponse> issueKakaoFirebaseToken(
      @RequestBody KakaoFirebaseTokenRequest request) {
    return ResponseEntity.ok(kakaoAuthService.issueFirebaseCustomToken(request));
  }
}
