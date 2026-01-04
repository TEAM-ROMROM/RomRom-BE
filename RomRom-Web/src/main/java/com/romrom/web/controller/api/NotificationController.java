package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.notification.dto.NotificationRequest;
import com.romrom.notification.service.FcmTokenService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/notification")
@RequiredArgsConstructor
@Tag(
    name = "알림 API",
    description = "알림 관련 API 제공"
)
public class NotificationController implements NotificationControllerDocs {

  private final FcmTokenService fcmTokenService;

  /**
   * FCM 토큰 저장
   */
  @Override
  @PostMapping(value = "/post/token", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> saveFcmToken(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute NotificationRequest request) {
    request.setMember(customUserDetails.getMember());
    fcmTokenService.saveFcmToken(request);
    return ResponseEntity.ok().build();
  }
}