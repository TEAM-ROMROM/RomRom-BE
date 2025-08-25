package com.romrom.web.controller;


import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.service.ChatService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(
    name = "채팅 API",
    description = "채팅방, 채팅 메시지 관련 API 제공"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatController implements ChatControllerDocs{

  private final ChatService chatService;

  /**
   * 1:1 채팅방 생성 (이미 있으면 기존 방 반환)
   */
  @Override
  @PostMapping(value = "/rooms/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ChatRoomResponse> createRoom(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(chatService.createOneToOneRoom(request));
  }

  /**
   * 본인이 포함된 채팅방 조회
   */
  @Override
  @PostMapping(value = "/rooms/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ChatRoomResponse> getRooms(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(chatService.getRooms(request));
  }

  /**
   * 방 삭제 (본인 포함된 방만 가능)
   */
  @Override
  @PostMapping(value = "/rooms/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<Void> deleteRoom(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails) {
    request.setMember(customUserDetails.getMember());
    chatService.deleteRoom(request);
    return ResponseEntity.noContent().build();
  }

  /**
   * 최근 메시지 조회 (Pageable, createDate DESC)
   */
  @Override
  @PostMapping(value = "/rooms/messages/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<ChatRoomResponse> getRecentMessages(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails
  ) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(chatService.findRecentMessages(request));
  }
}