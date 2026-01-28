package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.notification.dto.NotificationHistoryRequest;
import com.romrom.notification.dto.NotificationHistoryResponse;
import com.romrom.notification.dto.NotificationRequest;
import com.romrom.notification.service.FcmTokenService;
import com.romrom.notification.service.NotificationHistoryService;
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
  private final NotificationHistoryService notificationHistoryService;

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

  @Override
  @PostMapping(value = "/get/notifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<NotificationHistoryResponse> getNotificationHistoryPage(
    @AuthenticationPrincipal CustomUserDetails customUserDetails,
    @ModelAttribute NotificationHistoryRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(notificationHistoryService.getNotificationHistoryPage(request));
  }

  @Override
  @PostMapping(value = "/get/un-read/count", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<NotificationHistoryResponse> getUnReadNotificationCount(
    @AuthenticationPrincipal CustomUserDetails customUserDetails,
    @ModelAttribute NotificationHistoryRequest request) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(notificationHistoryService.getUnReadNotificationCount(request));
  }

  @Override
  @PostMapping(value = "/update/read", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> markAsRead(
    @AuthenticationPrincipal CustomUserDetails customUserDetails,
    @ModelAttribute NotificationHistoryRequest request) {
    request.setMember(customUserDetails.getMember());
    notificationHistoryService.markAsRead(request);
    return ResponseEntity.ok().build();
  }

  @Override
  @PostMapping(value = "/update/all/read", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> markAllAsRead(
    @AuthenticationPrincipal CustomUserDetails customUserDetails,
    @ModelAttribute NotificationHistoryRequest request) {
    request.setMember(customUserDetails.getMember());
    notificationHistoryService.markAllAsRead(request);
    return ResponseEntity.ok().build();
  }

  @Override
  @PostMapping(value = "/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> deleteNotification(
    @AuthenticationPrincipal CustomUserDetails customUserDetails,
    @ModelAttribute NotificationHistoryRequest request) {
    request.setMember(customUserDetails.getMember());
    notificationHistoryService.deleteNotification(request);
    return ResponseEntity.ok().build();
  }

  @Override
  @PostMapping(value = "/delete/all", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> deleteAllNotifications(
    @AuthenticationPrincipal CustomUserDetails customUserDetails,
    @ModelAttribute NotificationHistoryRequest request) {
    request.setMember(customUserDetails.getMember());
    notificationHistoryService.deleteAllNotifications(request);
    return ResponseEntity.ok().build();
  }
}