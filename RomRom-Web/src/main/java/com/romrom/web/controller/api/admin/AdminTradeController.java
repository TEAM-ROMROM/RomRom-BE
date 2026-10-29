package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminTradeService;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 거래 API: 목록, 상세, 강제 취소·완료
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminTradeController implements AdminTradeControllerDocs {

    private final AdminTradeService adminTradeService;

    @Override
    @PostMapping(value = "/trades/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getTrades(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminTradeService.getTradesForAdmin(request));
    }

    @Override
    @PostMapping(value = "/trades/detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getTradeDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminTradeService.getTradeDetail(request));
    }

    @Override
    @PostMapping(value = "/trades/force-cancel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> forceTradeCancel(@ModelAttribute AdminRequest request) {
        adminTradeService.forceCancel(request);
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping(value = "/trades/force-complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> forceTradeComplete(@ModelAttribute AdminRequest request) {
        adminTradeService.forceComplete(request);
        return ResponseEntity.ok().build();
    }
}
