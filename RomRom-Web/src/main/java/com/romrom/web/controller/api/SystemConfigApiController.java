package com.romrom.web.controller.api;

import com.romrom.web.service.SystemConfigService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/config")
@Slf4j
public class SystemConfigApiController {

  private final SystemConfigService systemConfigService;

  @GetMapping("/ai")
  @LogMonitor
  public ResponseEntity<Map<String, String>> getAiConfig() {
    return ResponseEntity.ok(systemConfigService.getAiConfig());
  }

  @PutMapping("/ai")
  @LogMonitor
  public ResponseEntity<Map<String, String>> updateAiConfig(@RequestBody Map<String, String> aiConfigMap) {
    systemConfigService.updateAiConfig(aiConfigMap);
    return ResponseEntity.ok(systemConfigService.getAiConfig());
  }

  @PostMapping("/cache/reload")
  @LogMonitor
  public ResponseEntity<Void> reloadCache() {
    systemConfigService.reloadCache();
    return ResponseEntity.ok().build();
  }
}
