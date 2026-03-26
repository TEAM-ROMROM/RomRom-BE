package com.romrom.web.controller.api;

import com.romrom.auth.dto.AuthRequest;
import com.romrom.auth.dto.AuthResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.dto.LoginRequest;
import com.romrom.auth.dto.SuspendedMemberResponse;
import com.romrom.auth.service.AuthService;
import com.romrom.common.exception.SuspendedMemberException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.HttpStatus;
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
@Tag(
    name = "인증 API",
    description = "회원 인증(Firebase 소셜 로그인) 관련 API 제공"
)
@RequestMapping("/api/auth")
public class AuthController implements AuthControllerDocs {
  private final AuthService authService;

  @Override
  @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
  @LogMonitor
  public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  @Override
  @PostMapping(value = "/reissue", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<?> reissue(@ModelAttribute AuthRequest request) {
    try {
      return ResponseEntity.ok(authService.reissue(request));
    } catch (SuspendedMemberException suspendedMemberException) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(SuspendedMemberResponse.builder()
              .errorCode("SUSPENDED_MEMBER")
              .suspendReason(suspendedMemberException.getSuspendReason())
              .suspendedUntil(suspendedMemberException.getSuspendedUntil())
              .build());
    }
  }

  @Override
  @PostMapping(value = "/logout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute AuthRequest request) {
    request.setMember(customUserDetails.getMember());
    authService.logout(request);
    return ResponseEntity.ok().build();
  }
}
