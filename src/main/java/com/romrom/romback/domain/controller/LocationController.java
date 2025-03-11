package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.dto.LocationRequest;
import com.romrom.romback.domain.service.LocationService;
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

@Tag(
    name = "위치 API",
    description = "위치 관련 API 제공"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/location")
public class LocationController implements LocationControllerDocs {

  private final LocationService locationService;

  @PostMapping(value = "/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  @Override
  public ResponseEntity<Void> saveLocation(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute LocationRequest request) {
    request.setMember(customUserDetails.getMember());
    locationService.saveLocation(request);
    return ResponseEntity.ok().build();
  }
}
