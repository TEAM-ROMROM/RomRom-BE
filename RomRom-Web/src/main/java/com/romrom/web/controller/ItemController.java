package com.romrom.web.controller;

import com.romrom.ai.AiService;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import com.romrom.item.service.ItemService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
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
  private final AiService aiService;

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
  public ResponseEntity<ItemResponse> postLike(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(itemService.likeOrUnlikeItem(request));
  }

  @Override
  @PostMapping(value = "/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ItemResponse> getItem(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemRequest request) {
    return ResponseEntity.ok(itemService.getItemsSortsByCreatedDate(request));
  }

  @Override
  @PostMapping(value = "/put", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ItemResponse> updateItem(
    @AuthenticationPrincipal CustomUserDetails customUserDetails,
    @ModelAttribute ItemRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(itemService.updateItem(request));
  }

  @Override
  @PostMapping(value = "/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ItemResponse> deleteItem(
    @AuthenticationPrincipal CustomUserDetails customUserDetails,
    @ModelAttribute ItemRequest request) {
    request.setMember(customUserDetails.getMember());
    itemService.deleteItem(request);
    return ResponseEntity.ok().build();
  }

  @Override
  @PostMapping(value = "/price/predict", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Integer> getItemPrice(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(aiService.predictItemPrice(request));
  }
}
