package com.romrom.web.controller;

import com.romrom.application.service.MemberApplicationService;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.dto.MemberResponse;
import com.romrom.member.service.MemberLocationService;
import com.romrom.member.service.MemberService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
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
  private final MemberApplicationService memberApplicationService;

  @Override
  @PostMapping(value = "/terms", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<MemberResponse> termsAgreement(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(memberApplicationService.saveTermsAgreement(request));
  }

  /**
   * 회원 선호 카테고리 저장 API
   * List<Integer> 형식으로 선호 카테고리 코드를 전송합니다.
   */
  @Override
  @PostMapping(value = "/post/category/preferences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
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
  @LogMonitor
  public ResponseEntity<Void> saveMemberLocation(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request) {
    request.setMember(customUserDetails.getMember());
    memberLocationService.saveMemberLocation(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @Override
  @PostMapping(value = "/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<MemberResponse> getMemberInfo(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(memberService.getMemberInfo(request));
  }

  @Override
  @PostMapping(value = "/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> deleteMember(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request,
      HttpServletRequest httpServletRequest) {
    request.setMember(customUserDetails.getMember());
    memberApplicationService.deleteMember(request, httpServletRequest);
    return ResponseEntity.ok().build();
  }
}
