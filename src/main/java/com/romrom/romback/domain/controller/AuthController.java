package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.dto.AuthRequest;
import com.romrom.romback.domain.service.MemberService;
import com.romrom.romback.global.aspect.LogMonitoringInvocation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(
    name = "인증 관련 API",
    description = "회원 인증 관련 API 제공"
)
@RequestMapping("/api/auth")
public class AuthController implements AuthControllerDocs {

  private final MemberService memberService;

  @Override
  @PostMapping(value = "/signup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<Void> signUp(
      @ModelAttribute AuthRequest request) {
    return ResponseEntity.ok(memberService.signUp(request));
  }

  @Override
  @PostMapping(value = "/login", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<Void> signIn(
      @ModelAttribute AuthRequest request) {
    return ResponseEntity.ok().build();
  }
}
