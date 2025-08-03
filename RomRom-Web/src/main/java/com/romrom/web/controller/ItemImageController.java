package com.romrom.web.controller;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.item.dto.ItemImageRequest;
import com.romrom.item.dto.ItemImageResponse;
import com.romrom.item.service.ItemImageService;
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
@RequestMapping("/api/item/image")
@RequiredArgsConstructor
@Tag(
    name = "물품 사진 관련 API",
    description = "물품 사진 관련 API 제공"
)
public class ItemImageController implements ItemImageControllerDocs{

  private final ItemImageService itemImageService;

  @Override
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ItemImageResponse> uploadItemImages(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemImageRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(itemImageService.saveItemImages(request));
  }

  @Override
  @PostMapping(value = "/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> deleteItemImages(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemImageRequest request) {
    request.setMember(customUserDetails.getMember());
    itemImageService.deleteItemImages(request);
    return ResponseEntity.ok().build();
  }
}
