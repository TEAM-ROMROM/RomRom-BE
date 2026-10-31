package com.romrom.web.controller.api.admin;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.application.service.AdminItemOperationService;
import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminItemService;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 물품 API: 목록, 상세, 수정, 상태 변경, 삭제, 숨김·해제
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminItemController implements AdminItemControllerDocs {

    private final AdminItemService adminItemService;
    private final AdminItemOperationService adminItemOperationService;

    @Override
    @PostMapping(value = "/items/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getItems(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminItemService.getItemsForAdmin(request));
    }

    @Override
    @PostMapping(value = "/items/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> deleteItem(@ModelAttribute AdminRequest request) {
        adminItemService.deleteItemByAdmin(request.getItemId(), request.getItemAdminDeleteReason(), request.getItemAdminDeleteDetail());
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping(value = "/items/update-status", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> updateItemStatus(@ModelAttribute AdminRequest request) {
        adminItemService.updateItemStatus(request);
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping(value = "/items/detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getItemDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminItemService.getItemDetail(request));
    }

    @Override
    @PostMapping(value = "/items/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> updateItem(@ModelAttribute AdminRequest request) {
        adminItemService.updateItem(request);
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping(value = "/items/hide", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> hideItem(@ModelAttribute AdminRequest request) {
        adminItemService.hideItem(request);
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping(value = "/items/unhide", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> unhideItem(@ModelAttribute AdminRequest request) {
        adminItemService.unhideItem(request);
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping(value = "/items/operation-status", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getItemOperationStatus(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminItemOperationService.getItemOperationStatus(request));
    }

    @Override
    @PostMapping(value = "/items/regenerate-embedding", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> regenerateItemEmbedding(@ModelAttribute AdminRequest request,
        @AuthenticationPrincipal CustomUserDetails principal) {
        request.setExecutorAdminId(principal.getMember().getMemberId());
        return ResponseEntity.ok(adminItemOperationService.regenerateItemEmbedding(request));
    }

    @Override
    @PostMapping(value = "/items/end-promotion", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> endItemPromotion(@ModelAttribute AdminRequest request,
        @AuthenticationPrincipal CustomUserDetails principal) {
        request.setExecutorAdminId(principal.getMember().getMemberId());
        return ResponseEntity.ok(adminItemOperationService.endItemPromotion(request));
    }
}
