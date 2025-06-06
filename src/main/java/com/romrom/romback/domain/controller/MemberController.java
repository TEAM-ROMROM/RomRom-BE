package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.dto.*;
import com.romrom.romback.domain.service.AuthService;
import com.romrom.romback.domain.service.MemberLocationService;
import com.romrom.romback.domain.service.MemberService;
import com.romrom.romback.global.aspect.LogMonitoringInvocation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "회원 API",
    description = "회원 관련 API 제공"
)
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController implements MemberControllerDocs {

  private final MemberService memberService;
  private final MemberLocationService memberLocationService;

  @Override
  @PostMapping(value = "/terms", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<AuthResponse> termsAgreement(
          @AuthenticationPrincipal CustomUserDetails customUserDetails,
          @ModelAttribute AuthRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(memberService.saveTermsAgreement(request));
  }

  /**
   * 회원 선호 카테고리 저장 API
   * List<Integer> 형식으로 선호 카테고리 코드를 전송합니다.
   */
  @Override
  @PostMapping(value = "/post/category/preferences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<Void> saveMemberProductCategories(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request) {
    request.setMember(customUserDetails.getMember());
    memberService.saveMemberProductCategories(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /**
   * 회원 위치정보 저장 API
   */
  @Override
  @PostMapping(value = "/post/location", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<Void> saveMemberLocation(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request) {
    request.setMember(customUserDetails.getMember());
    memberLocationService.saveMemberLocation(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @Override
  @PostMapping(value = "/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<MemberResponse> getMemberInfo(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(memberService.getMemberInfo(request));
  }

  @Override
  @PostMapping(value = "/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<Void> deleteMember(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request,
      HttpServletRequest httpServletRequest) {
    request.setMember(customUserDetails.getMember());
    memberService.deleteMember(request, httpServletRequest);
    return ResponseEntity.ok().build();
  }
}
