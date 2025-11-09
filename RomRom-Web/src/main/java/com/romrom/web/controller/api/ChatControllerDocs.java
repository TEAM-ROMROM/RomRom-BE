package com.romrom.web.controller.api;

import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;
import com.romrom.auth.dto.CustomUserDetails;

public interface ChatControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.11.04",
          author = Author.WISEUNGJAE,
          issueNumber = 318,
          description = "채팅방별 읽지 않은 메시지 개수 제공, 반환값에 Member 위치, 마지막으로 읽은 메시지 내용 추가"
      ),
      @ApiChangeLog(
          date = "2025.08.24",
          author = Author.WISEUNGJAE,
          issueNumber = 295,
          description = "사용자 1대1 채팅방 목록 조회 API 구현"
      )
  })
  @Operation(
      summary = "내 채팅방 목록 조회",
      description = """
    ### 인증(JWT): **필수**

    ### 요청 파라미터 (ChatRoomRequest)
    - `pageNumber` : 페이지 번호 (기본 0)
    - `pageSize` : 페이지 크기 (기본 30)

    ### 동작
    - 로그인한 사용자가 속한 1:1 채팅방 목록을 페이징으로 반환합니다.
    - 최신 생성일(createdDate) 순으로 정렬됩니다.

    ### 반환값 (ChatRoomResponse)
    - `chatRooms` (Page<ChatRoomDetailDto>): 내가 참여 중인 채팅방 목록과 각 방의 세부 정보 (위치, 마지막 메시지 등)
    """
  )
  ResponseEntity<ChatRoomResponse> getRooms(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.11.04",
          author = Author.WISEUNGJAE,
          issueNumber = 318,
          description = "채팅방 중복 생성 방지 기능 추가"
      ),
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
      - `opponentMemberId` (UUID): 대화 상대 사용자 ID
      - `tradeRequestHistoryId` (UUID): 거래 요청 ID
     
      ## 동작
      - 방이 없으면 생성, 있으면 기존 방 객체(ChatRoom)를 반환합니다.
      
      ## 반환값 (ChatRoomResponse)
      - `chatRoom` : 생성/기존 방 객체
      
      ## 에러코드
      - `CANNOT_CREATE_SELF_CHATROOM`: 자기 자신과는 채팅방을 생성할 수 없습니다.
      - `TRADE_REQUEST_NOT_FOUND`: 거래 요청을 찾을 수 없습니다.
      - `NOT_TRADE_REQUEST_RECEIVER`: 거래 요청을 받은 사용자만 생성할 수 있습니다.
      - `NOT_TRADE_REQUEST_SENDER`: 상대방이 거래 요청의 발신자가 아닙니다.
      - `MEMBER_NOT_FOUND`: 상대방 회원을 찾을 수 없습니다.
      """
  )
  ResponseEntity<ChatRoomResponse> createRoom(ChatRoomRequest request, CustomUserDetails customUserDetails);

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
      - `chatRoomId` (UUID) : 채팅방 ID
      
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
      - `chatRoomId` (UUID) : 채팅방 ID
      
      ## 동작
      - 요청 사용자가 방 멤버인 경우에만 조회
      - 최근 메시지 pageable 조회 (최신순)
      
      ## 반환값 (ChatRoomResponse)
      - `chatRoom` : 기존 방 객체
      - `messages` (Page<ChatMessage>): 최근 메시지 Page
     
      ## 에러코드
      - `CHATROOM_NOT_FOUND`: 채팅방을 찾을 수 없습니다.
      - `NOT_CHATROOM_MEMBER`: 채팅방의 멤버만 접근할 수 있는 권한입니다.
      """
  )
  ResponseEntity<ChatRoomResponse> getRecentMessages(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.10.14",
          author = Author.WISEUNGJAE,
          issueNumber = 318,
          description = "채팅방별 읽지 않은 메시지 개수 제공"
      )
  })
  @Operation(
      summary = "특정 채팅방의 읽음 표시 커서 갱신",
      description = """
      ## 인증(JWT): **필수**
      
      ## 요청 파라미터 (ChatRoomRequest)
      - `chatRoomId` (UUID) : 채팅방 ID
      - `isEntered` (boolean) : 사용자가 채팅방에 입장했는지 여부 (true: 입장, false: 퇴장)
      
      ## 동작
      - 특정 방에 속한 사용자(본인)의 읽음 표시 갱신
      - isEntered가 true면 leftAt을 null로 갱신하여, 입장 상태로 변경
      - isEntered가 false면 퇴장이므로, 현재 시각으로 leftAt 갱신
     
      ### leftAt은 본인이 나간 시간을 나타내며, 추후 읽지 않은 메시지 개수 계산에 사용됩니다.
      ### 입장 시, 퇴장 시 -> 웹소켓 구독/구독해제 이벤트와 함께 호출 필요
      
      ## 반환값
      - 200 OK
      
      ## 에러코드
      - `CHATROOM_NOT_FOUND`: 채팅방을 찾을 수 없습니다.
      - `NOT_CHATROOM_MEMBER`: 채팅방의 멤버만 접근할 수 있는 권한입니다.
      """
  )
  ResponseEntity<Void> updateReadCursor(ChatRoomRequest request, CustomUserDetails customUserDetails);
}