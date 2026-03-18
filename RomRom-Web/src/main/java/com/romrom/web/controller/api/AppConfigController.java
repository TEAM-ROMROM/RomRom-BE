package com.romrom.web.controller.api;

import com.romrom.common.annotation.SecuredApi;
import com.romrom.application.service.AppConfigService;
import com.romrom.common.dto.SystemResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(
    name = "앱 설정 API",
    description = "앱 버전 체크 및 업데이트 API 제공"
)
@RequestMapping("/api/app")
@Slf4j
public class AppConfigController implements AppConfigControllerDocs {

  private final AppConfigService appConfigService;

  @Override
  @PostMapping("/version/check")
  @SecuredApi
  @LogMonitor
  public ResponseEntity<SystemResponse> checkVersion() {
    return ResponseEntity.ok(appConfigService.checkVersion());
  }

  @Override
  @PostMapping(value = "/version/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @SecuredApi
  @LogMonitor
  public ResponseEntity<SystemResponse> updateLatestVersion(@RequestParam("version") String version) {
    return ResponseEntity.ok(appConfigService.updateLatestVersion(version));
  }
}
