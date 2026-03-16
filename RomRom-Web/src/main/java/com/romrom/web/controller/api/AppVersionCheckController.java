package com.romrom.web.controller.api;

import com.romrom.web.dto.SystemResponse;
import com.romrom.web.service.AppVersionCheckService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(
    name = "앱 버전 API",
    description = "앱 버전 체크 및 강제 업데이트 여부 조회 API 제공"
)
@RequestMapping("/api/app")
@Slf4j
public class AppVersionCheckController implements AppVersionCheckControllerDocs {

  private final AppVersionCheckService appVersionCheckService;

  @Override
  @PostMapping("/version/check")
  @LogMonitor
  public ResponseEntity<SystemResponse> checkVersion() {
    return ResponseEntity.ok(appVersionCheckService.checkVersion());
  }
}
