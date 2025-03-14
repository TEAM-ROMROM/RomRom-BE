package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.dto.MemberLocationRequest;
import com.romrom.romback.domain.service.MemberLocationService;
import com.romrom.romback.global.aspect.LogMonitoringInvocation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    name = "위치 API",
    description = "위치 관련 API 제공"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/location")
public class MemberLocationController implements MemberLocationControllerDocs {

  private final MemberLocationService memberLocationService;

  @PostMapping(value = "/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  @Override
  public ResponseEntity<Void> saveMemberLocation(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberLocationRequest request) {
    request.setMember(customUserDetails.getMember());
    memberLocationService.saveMemberLocation(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }
}
