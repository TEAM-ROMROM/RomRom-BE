package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminPerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 성능 지표 API
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminPerformanceController implements AdminPerformanceControllerDocs {

    private final AdminPerformanceService adminPerformanceService;

    // 화면 자동 갱신으로 자주 호출 > @LogMonitor 미적용 (로그 누적 방지)
    @Override
    @PostMapping(value = "/performance/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminResponse> getPerformanceSnapshot(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminPerformanceService.getPerformanceSnapshot());
    }
}
