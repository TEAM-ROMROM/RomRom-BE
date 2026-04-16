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
      @ApiChangeLog(date = "2026.04.06", author = Author.SUHSAECHAN, issueNumber = 617, description = "채팅방 리스트에 내 물품 대표 이미지 URL(myItemImageUrl) 추가"),
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
    - `myItemImageUrl` (String, nullable) : 내 교환 물품의 대표 이미지 URL (이미지 미등록 시 null)
    """
  )
  ResponseEntity<ChatRoomResponse> getRooms(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.04.17", author = Author.BAEKJIHOON, issueNumber = 650, description = "물품 ID 기반 채팅방 목록 조회 API 추가")
  })
  @Operation(
      summary = "물품별 채팅방 목록 조회",
      description = """
    ### 인증(JWT): **필수**

    ### 요청 파라미터 (ChatRoomRequest)
    - `itemId` (UUID) : 조회할 물품 ID (**필수**)
    - `pageNumber` : 페이지 번호 (기본 0)
    - `pageSize` : 페이지 크기 (기본 30)

    ### 동작
    - 로그인한 사용자가 속한 채팅방 중, 해당 물품(takeItem 또는 giveItem)이 포함된 채팅방 목록을 페이징으로 반환합니다.
    - 최신 생성일(createdDate) 순으로 정렬됩니다.
    - 본인이 나간(삭제한) 채팅방은 목록에 포함되지 않습니다.

    ### 반환값 (ChatRoomResponse)
    - `chatRoomDetailDtoPage` (Slice<ChatRoomDetailDto>): 해당 물품이 포함된 채팅방 목록과 각 방의 세부 정보
    아래는 ChatRoomDetailDto 정보입니다.
    - `chatRoomId` (UUID) : 채팅방 ID
    - `blocked` (boolean) : 차단 여부 (내가 상대방을 차단했거나 상대방이 나를 차단한 경우 true)
    - `targetMember` (Member) : 상대방 정보 (isOnline, lastActiveAt 포함)
    - `targetMemberEupMyeonDong` (String) : 상대방 위치 (읍면동)
    - `lastMessageContent` (String) : 마지막 메시지 내용
    - `lastMessageTime` (LocalDateTime) : 마지막 메시지가 생성된 시간
    - `unreadCount` (Long) : 안 읽은 메시지 개수
    - `chatRoomType` (ENUM) : 받은 요청, 보낸 요청 여부 (RECEIVED, REQUESTED)
    - `targetItemImageUrl` (String, nullable) : 상대방 물품의 대표 이미지 URL (이미지 미등록 시 null)
    - `myItemImageUrl` (String, nullable) : 내 교환 물품의 대표 이미지 URL (이미지 미등록 시 null)
    """
  )
  ResponseEntity<ChatRoomResponse> getRoomsByItemId(ChatRoomRequest request, CustomUserDetails customUserDetails);

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
      @ApiChangeLog(date = "2026.04.10", author = Author.WISEUNGJAE, issueNumber = 635, description = "최근 메시지 조회 응답에 AI 추천 정보(latestRecommendation) 추가"),
      @ApiChangeLog(date = "2026.03.14", author = Author.WISEUNGJAE, issueNumber = 572, description = "최근 메시지 조회 응답에 상대방 상태(opponentState) 추가"),
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
      - 읽음 여부는 `leftAt` 과 현재 입장 상태를 기준으로 서버가 계산합니다.
      
      ## 반환값 (ChatRoomResponse)
      - `chatRoom` : 기존 방 객체 (방 객체 안의 상대방 회원 정보에 lastActiveAt 및 isOnline 필드 true or false로 존재)
      - `messages` (Slice<ChatMessage>): 최근 메시지 Slice (Page와 유사하나, 다음 페이지 존재 여부만 제공, 총 개수 미제공)
      - `opponentState` : 상대방의 채팅방 상태
        - `memberId` : 상대방 회원 ID
        - `leftAt` : 상대방이 마지막으로 채팅방을 나간 시각 (현재 방 안에 있으면 null)
        - `isPresent` : 상대방이 현재 채팅방 화면에 있는지 여부
      - `latestRecommendation` : 현재 사용자를 기준으로 한 AI 추천 액션
        - `chatRoomId` : 추천이 속한 채팅방 ID
        - `targetMemberId` : 이 추천을 받는 현재 사용자 ID
        - `action` : `NONE`, `SEND_LOCATION`, `REQUEST_TRADE_COMPLETION`, `CANCEL_TRADE_COMPLETION_REQUEST`, `REJECT_TRADE_COMPLETION_REQUEST`, `CONFIRM_TRADE_COMPLETION`
        - `reason` : 추천 이유 (nullable)
        - `basedOnMessageId` : 어떤 최신 메시지 기준으로 판단했는지 식별자
        - `createdDate` : 추천 생성 시각
     
      ## 에러코드
      - `CHATROOM_NOT_FOUND`: 채팅방을 찾을 수 없습니다.
      - `NOT_CHATROOM_MEMBER`: 채팅방의 멤버만 접근할 수 있는 권한입니다.
      """
  )
  ResponseEntity<ChatRoomResponse> getRecentMessages(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.03.14", author = Author.WISEUNGJAE, issueNumber = 572, description = "읽음 커서 갱신 시 leftAt 기반 실시간 읽음 이벤트 연동"),
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
      - isEntered가 true면 leftAt을 null로 갱신하여, 입장 상태로 변경합니다.
      - isEntered가 false면 퇴장이므로, 현재 시각으로 leftAt 갱신합니다.
      - 서버는 leftAt(현재 입장 상태) 기반으로 마지막 읽은 메시지를 계산하여 WebSocket **읽음** 이벤트(`/sub/chat.read.{chatRoomId}`)를 발행합니다.
     
      ### leftAt이 null이면 현재 방 안에 있으므로 최신 메시지까지 읽은 것으로 간주합니다.
      ### leftAt이 존재하면 해당 시각 직전까지 읽은 것으로 간주합니다.
      ### 입장 시, 퇴장 시 -> 프론트에서 웹소켓 구독/구독해제 이벤트 호출 필요
      
      ## 반환값
      - 200 OK
      
      ## 에러코드
      - `CHATROOM_NOT_FOUND`: 채팅방을 찾을 수 없습니다.
      - `NOT_CHATROOM_MEMBER`: 채팅방의 멤버만 접근할 수 있는 권한입니다.
      """
  )
  ResponseEntity<Void> updateReadCursor(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.03.14", author = Author.WISEUNGJAE, issueNumber = 572, description = "채팅방 상대방 상태 조회 API 추가")
  })
  @Operation(
      summary = "특정 채팅방의 읽음 상태 조회",
      description = """
      ## 인증(JWT): **필수**

      ## 요청 파라미터 (ChatRoomRequest)
      - `chatRoomId` (UUID) : 채팅방 ID

      ## 동작
      - 현재 사용자의 상대방 상태만 반환합니다.
      - 프론트는 해당 정보를 기준으로 내가 보낸 메시지에 "읽음" 텍스트를 표시할 수 있습니다.

      ## 반환값 (ChatRoomResponse)
      - `opponentState` : 상대방 상태
      """
  )
  ResponseEntity<ChatRoomResponse> getReadStatus(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.03.31", author = Author.WISEUNGJAE, issueNumber = 612, description = "채팅방 교환 완료 요청 API 추가")
  })
  @Operation(
      summary = "채팅방 교환 완료 요청",
      description = """
      ## 인증(JWT): **필수**

      ## 요청 파라미터 (ChatRoomRequest)
      - `chatRoomId` (UUID) : 채팅방 ID

      ## 동작
      - 채팅 중(`CHATTING`) 상태에서만 교환 완료 요청을 보낼 수 있습니다.
      - 서버가 `TRADE_COMPLETE_REQUEST` 타입 시스템 메시지를 저장하고 브로드캐스팅합니다.
      - 이후 `messages/get` 으로 조회 시 해당 시스템 메시지를 기반으로 요청 대기 UI를 렌더링할 수 있습니다.

      ## 반환값
      - 200 OK

      ## 에러코드
      - `CHATROOM_NOT_FOUND`
      - `NOT_CHATROOM_MEMBER`
      - `TRADE_COMPLETION_REQUEST_NOT_ALLOWED`
      - `CANNOT_SEND_MESSAGE_TO_DELETED_CHATROOM`
      """
  )
  ResponseEntity<Void> requestTradeCompletion(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.03.31", author = Author.WISEUNGJAE, issueNumber = 612, description = "채팅방 교환 완료 요청 취소 API 추가")
  })
  @Operation(
      summary = "채팅방 교환 완료 요청 취소",
      description = """
      ## 인증(JWT): **필수**

      ## 요청 파라미터 (ChatRoomRequest)
      - `chatRoomId` (UUID) : 채팅방 ID

      ## 동작
      - 진행 중인 교환 완료 요청의 발신자만 취소할 수 있습니다.
      - 서버가 `TRADE_COMPLETE_REQUEST_CANCELED` 타입 시스템 메시지를 저장하고 브로드캐스팅합니다.

      ## 반환값
      - 200 OK

      ## 에러코드
      - `TRADE_COMPLETION_REQUEST_NOT_FOUND`
      - `TRADE_COMPLETION_REQUEST_FORBIDDEN`
      """
  )
  ResponseEntity<Void> cancelTradeCompletionRequest(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.03.31", author = Author.WISEUNGJAE, issueNumber = 612, description = "채팅방 교환 완료 요청 거절 API 추가")
  })
  @Operation(
      summary = "채팅방 교환 완료 요청 거절",
      description = """
      ## 인증(JWT): **필수**

      ## 요청 파라미터 (ChatRoomRequest)
      - `chatRoomId` (UUID) : 채팅방 ID

      ## 동작
      - 진행 중인 교환 완료 요청의 상대방만 거절할 수 있습니다.
      - 서버가 `TRADE_COMPLETE_REQUEST_REJECTED` 타입 시스템 메시지를 저장하고 브로드캐스팅합니다.
      - 거래 상태는 다시 `CHATTING` 으로 복귀합니다.

      ## 반환값
      - 200 OK

      ## 에러코드
      - `TRADE_COMPLETION_REQUEST_NOT_FOUND`
      - `TRADE_COMPLETION_REQUEST_FORBIDDEN`
      """
  )
  ResponseEntity<Void> rejectTradeCompletionRequest(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.03.31", author = Author.WISEUNGJAE, issueNumber = 612, description = "채팅방 교환 완료 요청 확인 API 추가")
  })
  @Operation(
      summary = "채팅방 교환 완료 요청 확인",
      description = """
      ## 인증(JWT): **필수**

      ## 요청 파라미터 (ChatRoomRequest)
      - `chatRoomId` (UUID) : 채팅방 ID

      ## 동작
      - 진행 중인 교환 완료 요청의 상대방만 확인할 수 있습니다.
      - 거래 상태를 `TRADED` 로 변경하고 양쪽 물품 상태를 `EXCHANGED` 로 반영합니다.
      - 서버가 `TRADE_COMPLETED` 타입 시스템 메시지를 저장하고 브로드캐스팅합니다.

      ## 반환값
      - 200 OK

      ## 에러코드
      - `TRADE_COMPLETION_REQUEST_NOT_FOUND`
      - `TRADE_COMPLETION_REQUEST_FORBIDDEN`
      """
  )
  ResponseEntity<Void> confirmTradeCompletion(ChatRoomRequest request, CustomUserDetails customUserDetails);
}
