package com.romrom.web.controller;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.notification.dto.NotificationRequest;
import com.romrom.notification.dto.NotificationResponse;
import com.romrom.notification.service.FcmTokenService;
import com.romrom.notification.service.NotificationService;
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
public class NotificationController {

  private final FcmTokenService fcmTokenService;
  private final NotificationService notificationService;

  /**
   * FCM 토큰 저장
   */
  @PostMapping(value = "/post/token", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<NotificationResponse> saveFcmToken(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute NotificationRequest request) {
    request.setMember(customUserDetails.getMember());
    NotificationResponse response = fcmTokenService.saveFcmToken(request);
    return ResponseEntity.ok(response);
  }

  /**
   * 특정 사용자에게 푸시 전송
   */
  @PostMapping(value = "/send/members", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> sendToMembers(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute NotificationRequest request) {
    request.setMember(customUserDetails.getMember());
    notificationService.sendToMembers(request);
    return ResponseEntity.ok().build();
  }

  /**
   * 전체 사용자에게 푸시 전송
   */
  @PostMapping(value = "/send/all", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> sendToAll(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute NotificationRequest request) {
    request.setMember(customUserDetails.getMember());
    notificationService.sendToAll(request);
    return ResponseEntity.ok().build();
  }
}