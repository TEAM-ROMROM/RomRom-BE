package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.service.ItemService;
import com.romrom.report.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
@RequestMapping("/api/item")
@RequiredArgsConstructor
@Tag(
    name = "물품 API",
    description = "물품 관련 API 제공"
)
public class ItemController implements ItemControllerDocs {

  private final ItemService itemService;
  private final ReportService reportService;

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
  @PostMapping(value = "/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ItemResponse> getItemDetail(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemRequest request) {
    request.setMember(customUserDetails.getMember());
    ItemResponse response = itemService.getItemDetail(request);

    // 본인 물품이 아닐 때만 신고 여부 확인
    if (!request.getMember().getMemberId().equals(response.getItem().getMember().getMemberId())) {
      response.getItem().setIsReported(reportService.isItemReportedByMember(
          request.getItemId(), request.getMember().getMemberId()));
    } else {
      response.getItem().setIsReported(false);
    }

    return ResponseEntity.ok(response);
  }

  @Override
  @PostMapping(value = "/get/my", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ItemResponse> getMyItems(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(itemService.getMyItemsFetchJoinMemberDesc(request));
  }

  @Override
  @PostMapping(value = "/list/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ItemResponse> getItemList(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemRequest request) {
    request.setMember(customUserDetails.getMember());
    ItemResponse response = itemService.getItemList(request);

    // 페이지 내 물품들의 신고 여부 배치 조회
    List<UUID> itemIds = response.getItemPage().getContent().stream()
        .map(Item::getItemId)
        .toList();
    Set<UUID> reportedItemIds = reportService.getReportedItemIds(
        request.getMember().getMemberId(), itemIds);

    // 각 Item에 isReported 세팅
    response.getItemPage().getContent().forEach(item ->
        item.setIsReported(reportedItemIds.contains(item.getItemId()))
    );

    return ResponseEntity.ok(response);
  }

  @Override
  @PostMapping(value = "/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> updateItem(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemRequest request) {
    request.setMember(customUserDetails.getMember());
    itemService.updateItem(request);
    return ResponseEntity.ok().build();
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
    return ResponseEntity.ok(itemService.predictItemPrice(request));
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
  @PostMapping(value = "/like/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ItemResponse> getLikedItems(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(itemService.getLikedItems(request));
  }

  @Override
  @PostMapping(value = "/status/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ItemResponse> updateTradeStatus(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute ItemRequest request
  ) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(itemService.updateItemStatus(request));
  }

}
