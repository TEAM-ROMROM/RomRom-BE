package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.dto.AuthRequest;
import com.romrom.romback.domain.object.dto.AuthResponse;
import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.service.AuthService;
import com.romrom.romback.global.aspect.LogMonitoringInvocation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
