package com.romrom.web.controller.api.admin;

import com.romrom.application.service.AdminOperationSettingService;
import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 시스템 설정 API: AI, 캐시, 앱 버전, UGC 필터, 점검 모드, 이미지
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminConfigController implements AdminConfigControllerDocs {

    private final SystemConfigService systemConfigService;
    private final AdminOperationSettingService adminOperationSettingService;

    @Override
    @PostMapping(value = "/config/ai/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAiConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getAiConfig());
    }

    @Override
    @PostMapping(value = "/config/ai/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateAiConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateAiConfig(request));
    }

    @Override
    @PostMapping(value = "/config/cache/reload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> reloadCache(@ModelAttribute AdminRequest request) {
        systemConfigService.reloadCache();
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping(value = "/config/app-version/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAppVersionConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getAppVersionConfig());
    }

    @Override
    @PostMapping(value = "/config/app-version/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateAppVersionConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateAppVersionConfig(request));
    }

    @Override
    @PostMapping(value = "/config/ugc-filter/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getUgcFilterConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getUgcFilterConfig());
    }

    @Override
    @PostMapping(value = "/config/ugc-filter/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateUgcFilterConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateUgcFilterConfig(request));
    }

    @Override
    @PostMapping(value = "/config/maintenance/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMaintenanceConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getMaintenanceConfig());
    }

    @Override
    @PostMapping(value = "/config/maintenance/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateMaintenanceConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateMaintenanceConfig(request));
    }

    @Override
    @PostMapping(value = "/config/image/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getImageConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getImageConfig());
    }

    @Override
    @PostMapping(value = "/config/image/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateImageConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateImageConfig(request));
    }

    @Override
    @PostMapping(value = "/config/chat-recommendation/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getChatRecommendationConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminOperationSettingService.getChatRecommendationConfig());
    }

    @Override
    @PostMapping(value = "/config/chat-recommendation/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateChatRecommendationConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminOperationSettingService.updateChatRecommendationConfig(request));
    }

    @Override
    @PostMapping(value = "/config/all/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAllSystemConfigs(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminOperationSettingService.getAllSystemConfigs());
    }
}
