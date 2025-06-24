package com.romrom.web.controller;

import com.romrom.auth.dto.AuthRequest;
import com.romrom.auth.dto.AuthResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.service.AuthService;
import com.romrom.common.aspect.LogMonitoringInvocation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(
    name = "인증 관련 API",
    description = "회원 인증(소셜 로그인) 관련 API 제공"
)
@RequestMapping("/api/auth")
public class AuthController implements AuthControllerDocs {
  private final AuthService authService;

  @Override
  @PostMapping(value = "/sign-in", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<AuthResponse> signIn(@ModelAttribute AuthRequest request) {
    return ResponseEntity.ok(authService.signIn(request));
  }


  @Override
  @PostMapping(value = "/reissue", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<AuthResponse> reissue(@ModelAttribute AuthRequest request) {
    return ResponseEntity.ok(authService.reissue(request));
  }

  @Override
  @PostMapping(value = "/logout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute AuthRequest request) {
    request.setMember(customUserDetails.getMember());
    authService.logout(request);
    return ResponseEntity.ok().build();
  }
}
