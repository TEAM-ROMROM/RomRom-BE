package com.romrom.web.controller;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.storage.dto.StorageRequest;
import com.romrom.storage.dto.StorageResponse;
import com.romrom.storage.service.StorageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/image")
@RequiredArgsConstructor
@Tag(
    name = "사진 API",
    description = "사진 관련 API 제공"
)
public class StorageController implements StorageControllerDocs {

  private final StorageService storageService;

  @Override
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<StorageResponse> uploadImages(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute StorageRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(storageService.saveImages(request));
  }

  @Override
  @PostMapping(value = "/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> deleteImages(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute StorageRequest request) {
    request.setMember(customUserDetails.getMember());
    storageService.deleteImages(request);
    return ResponseEntity.ok().build();
  }
}
