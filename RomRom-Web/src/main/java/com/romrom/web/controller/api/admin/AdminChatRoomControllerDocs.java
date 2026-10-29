package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
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
}
