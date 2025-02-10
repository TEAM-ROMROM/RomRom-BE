package com.romrom.romback.domain.authentication.controller;

import com.romrom.romback.domain.authentication.dto.SignInRequest;
import com.romrom.romback.domain.authentication.dto.SignUpRequest;
import com.romrom.romback.domain.member.service.MemberService;
import com.romrom.romback.global.aspect.LogMonitoringInvocation;
import com.romrom.romback.global.util.BaseResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(
    name = "인증 관련 API",
    description = "회원 인증 관련 API 제공"
)
public class AuthController implements AuthControllerDocs{

  private final MemberService memberService;

  @Override
  @PostMapping(value = "/api/auth/signup")
  @LogMonitoringInvocation
  public ResponseEntity<BaseResponse<Void>> signUp(
      @Valid @RequestBody SignUpRequest request) {
    return ResponseEntity.ok(memberService.signUp(request));
  }

  @Override
  @PostMapping(value = "/login", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<BaseResponse<Void>> signIn(SignInRequest request) {
    return ResponseEntity.ok(BaseResponse.success(null));
  }
}
