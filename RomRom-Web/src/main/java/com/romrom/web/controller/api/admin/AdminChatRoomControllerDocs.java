package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

public interface AdminChatRoomControllerDocs {

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.05", author = Author.SUHSAECHAN, issueNumber = 750, description = "채팅방 즉시 물리삭제를 soft delete + 배치 아카이브로 전환, 관리자 채팅방 관리 API 추가"),
    })
    @Operation(
        summary = "관리자 soft-delete 채팅방 목록 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data, 모두 선택)
        - **`pageNumber`** (Integer, 기본값 0): 페이지 번호
        - **`pageSize`** (Integer, 기본값 20): 페이지 크기
        - **`sortDirection`** (Sort.Direction, 기본값 DESC): deletedAt 기준 정렬 방향

        ## 동작 설명
        - soft-delete(삭제 시각 deletedAt 존재)된 청소 대기 채팅방만 조회합니다.
        - 정렬 기준은 deletedAt 으로 고정됩니다 (sortBy 무시).

        ## 반환값 (AdminResponse)
        - **`deletedChatRooms`**: 페이지네이션된 soft-delete 채팅방 목록
        - **`totalCount`**: 전체 청소 대기 채팅방 건수
        """
    )
    ResponseEntity<AdminResponse> getDeletedChatRooms(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.05", author = Author.SUHSAECHAN, issueNumber = 750, description = "채팅방 즉시 물리삭제를 soft delete + 배치 아카이브로 전환, 관리자 채팅방 관리 API 추가"),
    })
    @Operation(
        summary = "관리자 채팅방 상세 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`chatRoomId`** (UUID, 필수): 조회할 채팅방 ID

        ## 반환값 (AdminResponse)
        - **`chatRoom`**: 채팅방 엔티티 정보
        - **`chatMessages`**: 해당 채팅방의 전체 메시지 목록 (작성일 오름차순)

        ## 에러코드
        - CHATROOM_NOT_FOUND (404): 해당 chatRoomId의 채팅방이 존재하지 않음
        """
    )
    ResponseEntity<AdminResponse> getChatRoomDetail(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.05", author = Author.SUHSAECHAN, issueNumber = 750, description = "채팅방 즉시 물리삭제를 soft delete + 배치 아카이브로 전환, 관리자 채팅방 관리 API 추가"),
    })
    @Operation(
        summary = "관리자 채팅방 백업 추출 (다운로드)",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`chatRoomId`** (UUID, 필수): 추출할 채팅방 ID

        ## 동작 설명
        - 채팅방(엔티티 + 메시지)을 JSON 으로 직렬화한 뒤 gzip 으로 압축한 파일을 다운로드합니다.
        - 응답은 `application/octet-stream` 이며 파일명은 `chat-room_{chatRoomId}.json.gz` 입니다.

        ## 에러코드
        - CHATROOM_NOT_FOUND (404): 해당 chatRoomId의 채팅방이 존재하지 않음
        - CHATROOM_EXPORT_FAILED (500): 추출/압축 처리 중 오류 발생
        """
    )
    ResponseEntity<Resource> exportChatRoom(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.05", author = Author.SUHSAECHAN, issueNumber = 750, description = "채팅방 즉시 물리삭제를 soft delete + 배치 아카이브로 전환, 관리자 채팅방 관리 API 추가"),
    })
    @Operation(
        summary = "관리자 채팅방 즉시 물리 삭제",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`chatRoomId`** (UUID, 필수): 즉시 삭제할 채팅방 ID

        ## 동작 설명
        - 삭제 전 반드시 파일로 아카이브(백업)한 뒤 물리 삭제를 수행합니다.
        - 백업에 실패하면 데이터 유실 방지를 위해 삭제를 중단하고 500 을 반환합니다.

        ## 에러코드
        - CHATROOM_NOT_FOUND (404): 해당 chatRoomId의 채팅방이 존재하지 않음
        - CHATROOM_EXPORT_FAILED (500): 백업 실패로 삭제 중단
        """
    )
    ResponseEntity<Void> forceDeleteChatRoom(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 838, description = "관리자 채팅방 전체 목록(진행 중 포함) 조회 API 추가"),
    })
    @Operation(
        summary = "관리자 채팅방 목록 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data, 모두 선택)
        - **`chatRoomState`** (String, 기본값 ALL): ALL 전체, ACTIVE 진행 중, DELETED 삭제 대기 (그 외 값은 ALL)
        - **`searchKeyword`** (String): 참여자 닉네임 또는 물품명 부분 일치
        - **`pageNumber`** (Integer, 기본값 0), **`pageSize`** (Integer, 기본값 20)
        - **`sortDirection`** (Sort.Direction, 기본값 DESC): 생성일 정렬 방향

        ## 반환값 (AdminResponse)
        - **`chatRooms`**: 채팅방 페이지 (요청자·수신자, 거래 요청과 양쪽 물품 포함, deletedAt 있으면 삭제 대기)
        - **`totalCount`**: 조건에 맞는 채팅방 수
        """
    )
    ResponseEntity<AdminResponse> searchChatRooms(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.11.01", author = Author.SUHSAECHAN, issueNumber = 711, description = "관리자 채팅방 메시지 페이지 조회 API 추가 (숨김 메시지도 원본 + isHidden)"),
    })
    @Operation(
        summary = "관리자 채팅방 메시지 페이지 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`chatRoomId`** (UUID, 필수): 조회할 채팅방 ID
        - **`pageNumber`** (Integer, 기본값 0), **`pageSize`** (Integer, 기본값 20)

        ## 반환값 (AdminResponse)
        - **`chatMessageSlice`**: 메시지 Slice (작성일 내림차순, content·hasNext 등)
          - 관리자 숨김 메시지도 원본 내용 그대로, **`isHidden`** true · **`hiddenAt`** · **`hiddenByAdminId`** 포함

        ## 에러코드
        - INVALID_REQUEST (400): chatRoomId 누락
        - CHATROOM_NOT_FOUND (404): 해당 chatRoomId의 채팅방이 존재하지 않음
        """
    )
    ResponseEntity<AdminResponse> getChatMessages(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.11.01", author = Author.SUHSAECHAN, issueNumber = 711, description = "부적절 메시지 숨김(soft delete) API 추가"),
    })
    @Operation(
        summary = "관리자 채팅 메시지 숨김",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`chatMessageId`** (String, 필수): 숨길 메시지 ID
        - **`chatAdminActionReason`** (String, 선택): 조치 사유 (감사 기록)

        ## 동작 설명
        - 메시지 원본은 보존하고 `hiddenAt`만 기록 (이미 숨김이면 변화 없음)
        - 회원 앱 조회(메시지 목록, 채팅방 목록 마지막 메시지)에서는 내용이 "관리자에 의해 가려진 메시지입니다."로 바뀌고 이미지·위치가 빠짐
        - 이미 전송된 WebSocket 메시지·푸시는 회수하지 않음 (다음 조회부터 마스킹)
        - 발신 회원 SanctionHistory에 CHAT_MESSAGE_HIDE 기록 (시스템 메시지는 기록 생략)

        ## 에러코드
        - INVALID_REQUEST (400): chatMessageId 누락
        - CHAT_MESSAGE_NOT_FOUND (404): 해당 메시지가 존재하지 않음
        """
    )
    ResponseEntity<AdminResponse> hideChatMessage(AdminRequest request, CustomUserDetails principal);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.11.01", author = Author.SUHSAECHAN, issueNumber = 711, description = "메시지 숨김 해제 API 추가"),
    })
    @Operation(
        summary = "관리자 채팅 메시지 숨김 해제",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`chatMessageId`** (String, 필수): 숨김 해제할 메시지 ID
        - **`chatAdminActionReason`** (String, 선택): 조치 사유 (감사 기록)

        ## 동작 설명
        - `hiddenAt` 제거 > 회원 조회에 원본 다시 노출 (숨김 아니면 변화 없음)
        - 발신 회원 SanctionHistory에 CHAT_MESSAGE_UNHIDE 기록

        ## 에러코드
        - INVALID_REQUEST (400): chatMessageId 누락
        - CHAT_MESSAGE_NOT_FOUND (404): 해당 메시지가 존재하지 않음
        """
    )
    ResponseEntity<AdminResponse> unhideChatMessage(AdminRequest request, CustomUserDetails principal);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.11.01", author = Author.SUHSAECHAN, issueNumber = 711, description = "채팅방 강제 종료 API 추가"),
    })
    @Operation(
        summary = "관리자 채팅방 강제 종료",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`chatRoomId`** (UUID, 필수): 종료할 채팅방 ID
        - **`chatAdminActionReason`** (String, 선택): 조치 사유 (감사 기록)

        ## 동작 설명
        - 진행 중 거래(CHATTING, TRADE_COMPLETE_REQUESTED)는 CANCELED로 변경
        - 채팅방 soft delete (deletedAt) > 양쪽 회원 목록에서 사라지고 메시지 전송 차단 (CANNOT_SEND_MESSAGE_TO_CLOSED_CHATROOM)
        - 메시지는 즉시 지우지 않음 (보관 기간 후 배치가 백업·정리, 즉시 영구 삭제는 force-delete)
        - 참여자 2명 SanctionHistory에 CHAT_ROOM_FORCE_CLOSE 기록

        ## 에러코드
        - INVALID_REQUEST (400): chatRoomId 누락
        - CHATROOM_NOT_FOUND (404): 해당 채팅방이 존재하지 않음
        """
    )
    ResponseEntity<AdminResponse> forceCloseChatRoom(AdminRequest request, CustomUserDetails principal);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.11.01", author = Author.SUHSAECHAN, issueNumber = 711, description = "채팅방 회원 강제 퇴장 API 추가"),
    })
    @Operation(
        summary = "관리자 채팅방 회원 강제 퇴장",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`chatRoomId`** (UUID, 필수): 대상 채팅방 ID
        - **`memberId`** (UUID, 필수): 퇴장시킬 회원 ID (채팅방 참여자)
        - **`chatAdminActionReason`** (String, 선택): 조치 사유 (감사 기록)

        ## 동작 설명
        - 회원이 직접 나가기와 같은 처리: 진행 중 거래 취소, 대상 회원 목록에서 방 제거, 상대에게 퇴장 시스템 메시지
        - 상대도 이미 나간 방이면 채팅방 soft delete
        - 퇴장된 회원은 그 방에 메시지 전송 불가 (CANNOT_SEND_MESSAGE_TO_CLOSED_CHATROOM)
        - 대상 회원 SanctionHistory에 CHAT_ROOM_FORCE_LEAVE 기록

        ## 에러코드
        - INVALID_REQUEST (400): chatRoomId 또는 memberId 누락
        - CHATROOM_NOT_FOUND (404): 해당 채팅방이 존재하지 않음
        - NOT_CHATROOM_MEMBER (403): memberId가 채팅방 참여자가 아님
        """
    )
    ResponseEntity<AdminResponse> forceLeaveChatRoom(AdminRequest request, CustomUserDetails principal);
}
