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
      @ApiChangeLog(date = "2026.02.25", author = Author.WISEUNGJAE, issueNumber = 541, description = "online 필드 isOnline로 수정"),
      @ApiChangeLog(date = "2026.02.09", author = Author.SUHSAECHAN, issueNumber = 491, description = "채팅방 리스트에 상대방 물품 대표 이미지 URL(targetItemImageUrl) 추가"),
      @ApiChangeLog(date = "2026.02.01", author = Author.WISEUNGJAE, issueNumber = 467, description = "조회 오류 수정 및 코드 리팩토링"),
      @ApiChangeLog(date = "2026.01.31", author = Author.WISEUNGJAE, issueNumber = 459, description = "targetMember 안에 isOnline 필드 추가"),
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "차단된 회원 여부 반환 추가 (boolean blocked)"),
      @ApiChangeLog(date = "2025.11.04", author = Author.WISEUNGJAE, issueNumber = 318, description = "채팅방별 읽지 않은 메시지 개수 제공, 반환값에 Member 위치, 마지막으로 읽은 메시지 내용 추가"),
      @ApiChangeLog(date = "2025.08.24", author = Author.WISEUNGJAE, issueNumber = 295, description = "사용자 1대1 채팅방 목록 조회 API 구현")
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
    - 본인이 나간 채팅방은 목록에 포함되지 않습니다.
    - 각 채팅방은 chatRoomType (RECEIVED/REQUESTED)을 포함하여 보낸 요청/받은 요청 구분이 가능합니다.

    ### 반환값 (ChatRoomResponse)
    - `chatRoomDetailDtoPage` (Slice<ChatRoomDetailDto>): 내가 참여 중인 채팅방 목록과 각 방의 세부 정보 (위치, 마지막 메시지 등)
    아래는 ChatRoomDetailDto 정보입니다.
    - `chatRoomId` (UUID) : 채팅방 ID
    - `blocked` (boolean) : 차단 여부 (내가 상대방을 차단했거나 상대방이 나를 차단한 경우 true)
    - `targetMember` (Member) : 상대방 정보 (isOnline,lastActiveAt 포함) 
    - `targetMemberEupMyeonDong` (String) : 상대방 위치 (읍면동)
    - `lastMessageContent` (String) : 마지막 메시지 내용
    - `lastMessageTime` (LocalDateTime) : 마지막 메시지가 생성된 시간
    - `unreadCount` (Long) : 안 읽은 메시지 개수
    - `chatRoomType` (ENUM) : 받은 요청, 보낸 요청 여부 (RECEIVED, REQUESTED)
    - `targetItemImageUrl` (String, nullable) : 상대방 물품의 대표 이미지 URL (이미지 미등록 시 null)
    """
  )
  ResponseEntity<ChatRoomResponse> getRooms(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.01", author = Author.WISEUNGJAE, issueNumber = 467, description = "생성 시 대기중인 요청만 채팅방 생성 가능하도록 수정"),
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "차단된 회원과의 채팅방 생성을 방지하는 검증 로직 추가"),
      @ApiChangeLog(date = "2025.11.04", author = Author.WISEUNGJAE, issueNumber = 318, description = "채팅방 중복 생성 방지 기능 추가"),
      @ApiChangeLog(date = "2025.08.24", author = Author.WISEUNGJAE, issueNumber = 295, description = "사용자 1대1 채팅 기능 구현")
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
      - `TRADE_REQUEST_NOT_PENDING`: 거래 요청이 대기 상태가 아닙니다.
      - `CANNOT_CREATE_SELF_CHATROOM`: 자기 자신과는 채팅방을 생성할 수 없습니다.
      - `TRADE_REQUEST_NOT_FOUND`: 거래 요청을 찾을 수 없습니다.
      - `NOT_TRADE_REQUEST_RECEIVER`: 거래 요청을 받은 사용자만 생성할 수 있습니다.
      - `NOT_TRADE_REQUEST_SENDER`: 상대방이 거래 요청의 발신자가 아닙니다.
      - `MEMBER_NOT_FOUND`: 상대방 회원을 찾을 수 없습니다.
      """
  )
  ResponseEntity<ChatRoomResponse> createRoom(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.01", author = Author.WISEUNGJAE, issueNumber = 467, description = "채팅방 삭제 시 softDelete 처리"),
      @ApiChangeLog(date = "2025.08.24", author = Author.WISEUNGJAE, issueNumber = 295, description = "사용자 1대1 채팅 기능 구현")
  })
  @Operation(
      summary = "채팅방 삭제",
      description = """
      ## 인증(JWT): **필수**
      
      ## 요청 파라미터 (ChatRoomRequest)
      - `chatRoomId` (UUID) : 채팅방 ID
      
      ### 동작
      - 요청 사용자가 방 멤버인 경우에만 삭제
      - 상대방이 나가지 않았을때 : 본인만 채팅방에서 나가고, 추후 해당 채팅방 조회 불가
      - 상대방도 나갔을때 : 채팅방 및 관련 모든 정보 완전 삭제
      
      ### 반환값
      - 204 No Content
      
      ### 에러코드
      - `CHATROOM_NOT_FOUND`: 채팅방을 찾을 수 없습니다.
      - `NOT_CHATROOM_MEMBER`: 채팅방의 멤버만 접근할 수 있는 권한입니다.
      """
  )
  ResponseEntity<Void> deleteRoom(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.25", author = Author.WISEUNGJAE, issueNumber = 541, description = "최근 메시지 조회 시 상대방의 isOnline 필드 추가"),
      @ApiChangeLog(date = "2025.08.24", author = Author.WISEUNGJAE, issueNumber = 295, description = "사용자 1대1 채팅 기능 구현")
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
      - `chatRoom` : 기존 방 객체 (방 객체 안의 상대방 회원 정보에 lastActiveAt 및 isOnline 필드 true or false로 존재)
      - `messages` (Slice<ChatMessage>): 최근 메시지 Slice (Page와 유사하나, 다음 페이지 존재 여부만 제공, 총 개수 미제공)
     
      ## 에러코드
      - `CHATROOM_NOT_FOUND`: 채팅방을 찾을 수 없습니다.
      - `NOT_CHATROOM_MEMBER`: 채팅방의 멤버만 접근할 수 있는 권한입니다.
      """
  )
  ResponseEntity<ChatRoomResponse> getRecentMessages(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.10.14", author = Author.WISEUNGJAE, issueNumber = 318, description = "채팅방별 읽지 않은 메시지 개수 제공")
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