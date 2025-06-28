package com.romrom.web.controller;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.item.dto.ItemDetailResponse;
import com.romrom.item.dto.ItemFilteredRequest;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import com.romrom.item.dto.LikeRequest;
import com.romrom.item.dto.LikeResponse;
import com.romrom.item.service.ItemService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
  @LogMonitor
  public ResponseEntity<ItemResponse> postItem(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(itemService.postItem(request));
  }

  @Override
  @PostMapping(value = "/like/post", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<LikeResponse> postLike(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute LikeRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(itemService.likeOrUnlikeItem(request));
  }

  @Override
  @PostMapping(value = "/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Page<ItemDetailResponse>> getItem(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemFilteredRequest request) {
    return ResponseEntity.ok(itemService.getItemsSortsByCreatedDate(request));
  }

  @Override
  @PostMapping(value = "/put", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ItemResponse> updateItem(
    @AuthenticationPrincipal CustomUserDetails customUserDetails,
    ItemRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(itemService.updateItem(request));
  }

  @Override
  @PostMapping(value = "/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ItemResponse> deleteItem(
    @AuthenticationPrincipal CustomUserDetails customUserDetails,
    ItemRequest request) {
    request.setMember(customUserDetails.getMember());
    itemService.deleteItem(request);
    return ResponseEntity.ok().build();
  }
}
