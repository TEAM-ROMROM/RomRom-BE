package com.romrom.web.controller.api;

import com.romrom.notification.dto.HomePopupResponse;
import com.romrom.notification.service.HomePopupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 홈 팝업 API: 활성 팝업 1건 조회 (인증 불필요, SecurityUrls.AUTH_WHITELIST)
 */
@RestController
@RequestMapping("/api/home-popups")
@RequiredArgsConstructor
@Tag(
    name = "홈 팝업 API",
    description = "앱 첫 화면 공지·이벤트 팝업 조회 API 제공"
)
public class HomePopupController implements HomePopupControllerDocs {

  private final HomePopupService homePopupService;

  @Override
  @GetMapping("/active")
  @LogMonitor
  public ResponseEntity<HomePopupResponse> getActiveHomePopup() {
    return ResponseEntity.ok(homePopupService.getActiveHomePopup());
  }
}
