package com.romrom.web.controller.api;


import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.service.ChatMessageService;
import com.romrom.chat.service.ChatRoomService;
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

@Tag(
    name = "채팅 API",
    description = "채팅방, 채팅 메시지 관련 API 제공"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/rooms")
public class ChatController implements ChatControllerDocs {

  private final ChatRoomService chatRoomService;
  private final ChatMessageService chatMessageService;

  /**
   * 1:1 채팅방 생성 (이미 있으면 기존 방 반환)
   * 거래 요청을 받은 사람이 채팅방을 생성하는 로직이므로,
   * customUserDetails.getMember()는 거래 요청을 받은 사람임.
   * 즉 takeItem의 소유자임.
   */
  @Override
  @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ChatRoomResponse> createRoom(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(chatRoomService.createOneToOneRoom(request));
  }

  /**
   * 본인이 포함된 채팅방 조회
   */
  @Override
  @PostMapping(value = "/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ChatRoomResponse> getRooms(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(chatRoomService.getRooms(request));
  }

  /**
   * 방 삭제 (본인 포함된 방만 가능)
   */
  @Override
  @PostMapping(value = "/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> deleteRoom(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setMember(customUserDetails.getMember());
    chatRoomService.deleteRoom(request);
    return ResponseEntity.noContent().build();
  }

  /**
   * 최근 메시지 조회 (Pageable, createDate DESC)
   */
  @Override
  @PostMapping(value = "/messages/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ChatRoomResponse> getRecentMessages(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails
  ) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(chatMessageService.findRecentMessages(request));
  }

  /**
   * 읽음 커서 갱신
   */
  @Override
  @PostMapping(value = "/read-cursor/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> updateReadCursor(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails
  ) {
    request.setMember(customUserDetails.getMember());
    chatRoomService.enterOrLeaveChatRoom(request);
    return ResponseEntity.ok().build();
  }
}