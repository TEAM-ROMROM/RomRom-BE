package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.dto.ItemDetailResponse;
import com.romrom.romback.domain.object.dto.ItemFilteredRequest;
import com.romrom.romback.domain.object.dto.ItemRequest;
import com.romrom.romback.domain.object.dto.ItemResponse;
import com.romrom.romback.domain.object.dto.LikeRequest;
import com.romrom.romback.domain.object.dto.LikeResponse;
import com.romrom.romback.domain.service.ItemService;
import com.romrom.romback.global.aspect.LogMonitoringInvocation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/item")
@RequiredArgsConstructor
@Tag(
    name = "물품 관련 API",
    description = "물품 관련 API 제공"
)
public class ItemController implements ItemControllerDocs {

  private final ItemService itemService;

  @Override
  @PostMapping(value = "/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<ItemResponse> postItem(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(itemService.postItem(request));
  }

  @Override
  @PostMapping(value = "/like/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<LikeResponse> postLike(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute LikeRequest request) {
    request.setMember(customUserDetails.getMember());

    return ResponseEntity.ok(itemService.likeOrUnlikeItem(request));
  }

  @PostMapping(value = "/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitoringInvocation
  public ResponseEntity<Page<ItemDetailResponse>> getItem(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemFilteredRequest request) {
    return ResponseEntity.ok(itemService.getItemsSortsByCreatedDate(request));
  }
}
