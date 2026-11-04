package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface ChatRoomActionControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 860, description = "채팅방별 알림 끄기·켜기 API 추가")
  })
  @Operation(
      summary = "채팅방 알림 끄기·켜기",
      description = """
    ## 인증(JWT): **필수**

    ## 요청 파라미터 (ChatRoomRequest)
    - `chatRoomId` (UUID): 채팅방 ID (**필수**)
    - `isNotificationMuted` (Boolean): true 끄기, false 켜기 (**필수**)

    ## 동작
    - 내 이 방 설정만 바뀝니다. 끈 방은 채팅 메시지 FCM 푸시를 보내지 않고, 안 읽은 메시지 수는 그대로 셉니다.
    - 약속 리마인드 알림은 이 설정과 무관하게 발송됩니다.
    - 현재 값은 채팅방 목록(`ChatRoomDetailDto.isNotificationMuted`)과 메시지 조회(`ChatRoomResponse.isNotificationMuted`)로도 받습니다.

    ## 반환값 (ChatRoomResponse)
    - `isNotificationMuted` (Boolean): 변경 후 값

    ## 에러코드
    - `INVALID_REQUEST`: chatRoomId 또는 isNotificationMuted 누락
    - `CHATROOM_NOT_FOUND`, `NOT_CHATROOM_MEMBER`, `CHAT_USER_STATE_NOT_FOUND`
    """
  )
  ResponseEntity<ChatRoomResponse> updateNotificationMuted(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 860, description = "내 메시지 삭제 API 추가 (24시간 이내, soft delete, chat.update 토픽 송출)")
  })
  @Operation(
      summary = "내 메시지 삭제",
      description = """
    ## 인증(JWT): **필수**

    ## 요청 파라미터 (ChatRoomRequest)
    - `chatRoomId` (UUID): 채팅방 ID (**필수**)
    - `chatMessageId` (String): 삭제할 메시지 ID (**필수**)

    ## 동작
    - 내가 보낸 TEXT·IMAGE·LOCATION 메시지만, 보낸 지 24시간 이내에 삭제할 수 있습니다. 약속 메시지는 삭제 대신 취소합니다.
    - 원본은 보존(soft delete)하고, 회원 조회 응답에서는 content "삭제된 메시지입니다.", type TEXT, 사진·좌표·약속 제거, `isDeleted` true 로 내려갑니다.
    - 커밋 후 `/sub/chat.update.{chatRoomId}` 로 변경된 메시지를 송출합니다. (옛 앱은 이 토픽을 구독하지 않고, 다시 조회 시 안내 문구를 글자로 표시)

    ## 반환값 (ChatRoomResponse)
    - `chatMessage` (ChatMessage): 삭제 반영된 메시지

    ## 에러코드
    - `CHAT_MESSAGE_NOT_FOUND`: 메시지 없음 또는 다른 방 메시지
    - `CHAT_MESSAGE_DELETE_NOT_SENDER`: 내가 보낸 메시지가 아님
    - `CHAT_MESSAGE_DELETE_PERIOD_EXPIRED`: 보낸 지 24시간 지남
    - `CHAT_MESSAGE_NOT_DELETABLE`: 이미 삭제·관리자 숨김·약속·시스템 메시지
    - `CHATROOM_NOT_FOUND`, `NOT_CHATROOM_MEMBER`, `INVALID_REQUEST`
    """
  )
  ResponseEntity<ChatRoomResponse> deleteMyMessage(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 860, description = "약속 제안 API 추가 (TEXT 메시지 + appointment 필드)")
  })
  @Operation(
      summary = "약속 제안",
      description = """
    ## 인증(JWT): **필수**

    ## 요청 파라미터 (ChatRoomRequest)
    - `chatRoomId` (UUID): 채팅방 ID (**필수**)
    - `appointmentAt` (LocalDateTime, KST): 약속 일시 (**필수**, 지금 이후 90일 이내) 예: `2026-11-06T15:00:00`
    - `placeName` (String): 장소 이름 (**필수**, 1~50자)
    - `latitude`, `longitude` (Double): 장소 좌표 (선택, 둘 다 보내거나 둘 다 생략)

    ## 동작
    - 옛 앱 호환을 위해 새 메시지 타입 없이 `type` TEXT 로 저장·송출합니다.
      - `content`: `[약속] 11월 6일 (목) 오후 3:00 · 군자역` (옛 앱은 이 글자를 표시)
      - `appointment`: `{ appointmentAt, placeName, latitude, longitude, status: PROPOSED, statusChangedMemberId }` (새 앱은 카드 표시)
    - 일반 메시지와 같이 `/sub/chat.room.{chatRoomId}` 로 송출하고, 상대가 방 밖이면 채팅 푸시를 보냅니다 (상대가 방 알림을 끈 경우 제외).

    ## 반환값 (ChatRoomResponse)
    - `chatMessage` (ChatMessage): 저장된 약속 메시지

    ## 에러코드
    - `INVALID_CHAT_APPOINTMENT`: 일시(과거·90일 초과)·장소·좌표 오류
    - 채팅 전송과 같은 방 상태 오류: `CANNOT_SEND_MESSAGE_TO_CLOSED_CHATROOM`, `CANNOT_SEND_MESSAGE_TO_DELETED_CHATROOM`, `CANNOT_SEND_MESSAGE_TO_ADMIN_DELETED_ITEM_CHATROOM`, 차단 오류
    - `CHATROOM_NOT_FOUND`, `NOT_CHATROOM_MEMBER`
    """
  )
  ResponseEntity<ChatRoomResponse> proposeAppointment(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 860, description = "약속 수락 API 추가")
  })
  @Operation(
      summary = "약속 수락",
      description = """
    ## 인증(JWT): **필수**

    ## 요청 파라미터 (ChatRoomRequest)
    - `chatRoomId` (UUID), `chatMessageId` (String): 약속 메시지 (**필수**)

    ## 동작
    - 제안 받은 사람만, PROPOSED 상태이고 약속 시각 전일 때 ACCEPTED 로 바꿉니다. (동시 요청은 1건만 성공)
    - 본문도 `[약속 확정] ...` 으로 갱신해 옛 앱은 다시 조회 시 확정 상태를 글자로 확인합니다.
    - 커밋 후 `/sub/chat.update.{chatRoomId}` 송출, 상대가 방 밖이고 알림 켜짐이면 "약속을 수락했어요" 채팅 푸시.
    - 확정 약속은 약속 1시간 전에 양쪽 회원에게 리마인드 푸시 (방별 알림 끄기 무시, `chatPushKind`=APPOINTMENT_REMINDER).

    ## 반환값 (ChatRoomResponse)
    - `chatMessage` (ChatMessage): 변경된 약속 메시지

    ## 에러코드
    - `CHAT_APPOINTMENT_ACCEPT_FORBIDDEN`: 제안한 사람이 수락 시도
    - `CHAT_APPOINTMENT_STATUS_CONFLICT`: 이미 확정·취소됨 또는 숨김 메시지
    - `INVALID_CHAT_APPOINTMENT`: 약속 시각 지남
    - `INVALID_REQUEST`: 약속 메시지가 아님, `CHAT_MESSAGE_NOT_FOUND`, 채팅 전송과 같은 방 상태 오류
    """
  )
  ResponseEntity<ChatRoomResponse> acceptAppointment(ChatRoomRequest request, CustomUserDetails customUserDetails);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 860, description = "약속 취소 API 추가")
  })
  @Operation(
      summary = "약속 취소",
      description = """
    ## 인증(JWT): **필수**

    ## 요청 파라미터 (ChatRoomRequest)
    - `chatRoomId` (UUID), `chatMessageId` (String): 약속 메시지 (**필수**)

    ## 동작
    - 양쪽 회원 모두 PROPOSED·ACCEPTED 약속을 CANCELED 로 바꿀 수 있습니다. 본문은 `[약속 취소] ...` 로 갱신.
    - 커밋 후 `/sub/chat.update.{chatRoomId}` 송출, 상대가 방 밖이고 알림 켜짐이면 "약속을 취소했어요" 채팅 푸시.

    ## 반환값 (ChatRoomResponse)
    - `chatMessage` (ChatMessage): 변경된 약속 메시지

    ## 에러코드
    - `CHAT_APPOINTMENT_STATUS_CONFLICT`: 이미 취소됨 또는 숨김 메시지
    - `INVALID_REQUEST`: 약속 메시지가 아님, `CHAT_MESSAGE_NOT_FOUND`, `CHATROOM_NOT_FOUND`, `NOT_CHATROOM_MEMBER`
    """
  )
  ResponseEntity<ChatRoomResponse> cancelAppointment(ChatRoomRequest request, CustomUserDetails customUserDetails);
}
