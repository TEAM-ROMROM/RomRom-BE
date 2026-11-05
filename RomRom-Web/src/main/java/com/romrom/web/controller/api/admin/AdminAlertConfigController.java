package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminAlertConfigService;
import com.romrom.common.annotation.AdminAudit;
import com.romrom.common.constant.AdminAuditAction;
import com.romrom.common.constant.AdminAuditTargetType;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 알림 설정 API: 조회, 수정
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminAlertConfigController implements AdminAlertConfigControllerDocs {

    private final AdminAlertConfigService adminAlertConfigService;

    @Override
    @PostMapping(value = "/alert-config/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAlertConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAlertConfigService.getAlertConfig());
    }

    @Override
    @PostMapping(value = "/alert-config/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAudit(action = AdminAuditAction.ALERT_CONFIG_UPDATE, targetType = AdminAuditTargetType.SYSTEM_CONFIG)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateAlertConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAlertConfigService.updateAlertConfig(request));
    }
}
