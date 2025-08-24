package com.romrom.web.controller;

import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;
import com.romrom.auth.dto.CustomUserDetails;

public interface ChatControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.08.24",
          author = Author.WISEUNGJAE,
          issueNumber = 295,
          description = "사용자 1대1 채팅 기능 구현"
      )
  })
  @Operation(
      summary = "1:1 채팅방 생성",
      description = """
      ## 인증(JWT): **필수**
      
      ## 요청 파라미터 (ChatRoomRequest)
      - `otherUserId` (UUID): 대화 상대 사용자 ID
     
      ## 동작
      - 방이 없으면 생성, 있으면 기존 방의 roomId 반환
      
      ## 반환값 (ChatRoomResponse)
      - `roomId` (String): 생성/기존 방 ID
      
      ## 에러코드
      - `CANNOT_CREATE_SELF_CHATROOM`: 자기 자신과는 채팅방을 생성할 수 없습니다.
      """
  )
  ResponseEntity<ChatResponse> createRoom(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.08.24",
          author = Author.WISEUNGJAE,
          issueNumber = 295,
          description = "사용자 1대1 채팅 기능 구현"
      )
  })
  @Operation(
      summary = "채팅방 삭제",
      description = """
      ## 인증(JWT): **필수**
      
      ## 요청 파라미터 (ChatRoomRequest)
      - `roomId` (UUID) : 채팅방 ID
      
      ### 동작
      - 요청 사용자가 방 멤버인 경우에만 삭제
      - 채팅방 메시지도 함께 삭제
      
      ### 반환값
      - 204 No Content
      
      ### 에러코드
      - `CHATROOM_NOT_FOUND`: 채팅방을 찾을 수 없습니다.
      - `NOT_CHATROOM_MEMBER`: 채팅방의 멤버만 접근할 수 있는 권한입니다.
      """
  )
  ResponseEntity<Void> deleteRoom(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.08.24",
          author = Author.WISEUNGJAE,
          issueNumber = 295,
          description = "사용자 1대1 채팅 기능 구현"
      )
  })
  @Operation(
      summary = "최근 메시지 조회",
      description = """
      ## 인증(JWT): **필수**
      
      ## 요청 파라미터 (ChatRoomRequest)
      - `roomId` (UUID) : 채팅방 ID
      
      ## 동작
      - 요청 사용자가 방 멤버인 경우에만 조회
      - 최근 메시지 pageable 조회 (최신순)
      
      ## 반환값 (ChatRoomResponse)
      - `roomId` (String): 생성/기존 방 ID
      - `messages` (Page<ChatMessage>): 최근 메시지 Page
     
      ## 에러코드
      - `CHATROOM_NOT_FOUND`: 채팅방을 찾을 수 없습니다.
      - `NOT_CHATROOM_MEMBER`: 채팅방의 멤버만 접근할 수 있는 권한입니다.
      """
  )
  ResponseEntity<ChatResponse> getRecentMessages(ChatRoomRequest request, CustomUserDetails customUserDetails);
}