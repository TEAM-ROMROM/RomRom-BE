package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.service.ChatAppointmentService;
import com.romrom.chat.service.ChatMessageDeleteService;
import com.romrom.chat.service.ChatRoomNotificationMuteService;
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

/**
 * 채팅 확장 API (#860): 방별 알림 끄기, 내 메시지 삭제, 약속 제안·수락·취소
 */
@Tag(
    name = "채팅 API",
    description = "채팅방, 채팅 메시지 관련 API 제공"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/rooms")
public class ChatRoomActionController implements ChatRoomActionControllerDocs {

  private final ChatRoomNotificationMuteService chatRoomNotificationMuteService;
  private final ChatMessageDeleteService chatMessageDeleteService;
  private final ChatAppointmentService chatAppointmentService;

  @Override
  @PostMapping(value = "/notification-mute/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ChatRoomResponse> updateNotificationMuted(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(chatRoomNotificationMuteService.updateNotificationMuted(request));
  }

  @Override
  @PostMapping(value = "/messages/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ChatRoomResponse> deleteMyMessage(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(chatMessageDeleteService.deleteMyMessage(request));
  }

  @Override
  @PostMapping(value = "/appointments/propose", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ChatRoomResponse> proposeAppointment(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(chatAppointmentService.proposeAppointment(request));
  }

  @Override
  @PostMapping(value = "/appointments/accept", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ChatRoomResponse> acceptAppointment(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(chatAppointmentService.acceptAppointment(request));
  }

  @Override
  @PostMapping(value = "/appointments/cancel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ChatRoomResponse> cancelAppointment(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(chatAppointmentService.cancelAppointment(request));
  }
}
