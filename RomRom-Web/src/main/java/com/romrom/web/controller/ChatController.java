package com.romrom.web.controller;


import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.service.ChatService;

import java.util.List;
import java.util.UUID;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    UUID roomId = chatService.createOneToOneRoom(request).getRoomId();

    return ResponseEntity.ok(ChatRoomResponse.builder().roomId(roomId).build());
  }

  /**
   * 방 삭제 (본인 포함된 방만)
   */
  @Override
  @PostMapping(value = "/rooms/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
  public ResponseEntity<ChatRoomResponse> getRecentMessages(
      @ModelAttribute ChatRoomRequest request,
      @AuthenticationPrincipal CustomUserDetails customUserDetails
  ) {
    request.setMember(customUserDetails.getMember());
    return ResponseEntity.ok(chatService.findRecentMessages(request));
  }
}