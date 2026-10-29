package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminMemberSanctionControllerDocs {

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원 강제 탈퇴 API 추가"
        )
    })
    @Operation(
        summary = "회원 강제 탈퇴",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수): 강제 탈퇴 대상
        - **`forceWithdrawReason`** (String, 필수): 탈퇴 사유

        ## 응답 (AdminResponse)
        - 빈 응답 (HTTP 200 OK)

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        - ADMIN_SELF_ACTION_FORBIDDEN (403): 본인 계정 강제 탈퇴 시도
        - MEMBER_ALREADY_DELETED (409): 이미 탈퇴된 회원
        """
    )
    ResponseEntity<AdminResponse> forceWithdrawMember(AdminRequest adminRequest, CustomUserDetails principal);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.06.13",
            author = Author.SUHSAECHAN,
            issueNumber = 713,
            description = "회원 일괄 정지 API 추가 — memberIds 배열, 회원 단위 독립 트랜잭션으로 부분 실패 격리, 개별 결과 반환"
        )
    })
    @Operation(
        summary = "회원 일괄 정지",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberIds`** (List<UUID>, 필수): 정지 대상 회원 목록
        - **`suspendReason`** (String, 선택): 정지 사유
        - **`suspendedUntil`** (String, 선택): 정지 종료 시각 (yyyy-MM-dd'T'HH:mm)

        ## 응답 (AdminResponse)
        - **`bulkActionResults`**: List<BulkActionResult> — 회원별 성공/실패 결과 (실패 시 failReason)

        ## 동작
        - 회원 단위 독립 트랜잭션으로 처리되어 한 건 실패가 전체를 롤백하지 않는다.

        ## 에러코드
        - INVALID_REQUEST (400): memberIds 누락
        """
    )
    ResponseEntity<AdminResponse> bulkSuspendMembers(AdminRequest adminRequest, CustomUserDetails principal);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.06.13",
            author = Author.SUHSAECHAN,
            issueNumber = 713,
            description = "회원 일괄 강제 탈퇴 API 추가 — memberIds 배열, 회원 단위 독립 트랜잭션으로 부분 실패 격리, 개별 결과 반환"
        )
    })
    @Operation(
        summary = "회원 일괄 강제 탈퇴",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberIds`** (List<UUID>, 필수): 강제 탈퇴 대상 회원 목록
        - **`forceWithdrawReason`** (String, 선택): 탈퇴 사유

        ## 응답 (AdminResponse)
        - **`bulkActionResults`**: List<BulkActionResult> — 회원별 성공/실패 결과 (실패 시 failReason)

        ## 동작
        - 회원 단위 독립 트랜잭션으로 처리되어 한 건 실패가 전체를 롤백하지 않는다.
        - 기존 회원 탈퇴 cascade 로직(forceWithdrawMember)을 재사용한다.

        ## 에러코드
        - INVALID_REQUEST (400): memberIds 누락
        """
    )
    ResponseEntity<AdminResponse> bulkWithdrawMembers(AdminRequest adminRequest, CustomUserDetails principal);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.29", author = Author.SUHSAECHAN, issueNumber = 814, description = "물품별 독립 트랜잭션으로 변경 (1건 실패가 성공 건까지 롤백하지 않음)"),
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원 보유 물품 일괄 삭제 API 추가"
        )
    })
    @Operation(
        summary = "회원 보유 물품 일괄 삭제",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수): 물품 소유 회원
        - **`itemIds`** (List<UUID>, 필수): 삭제 대상 물품 ID 목록
        - **`itemAdminDeleteDetail`** (String, 선택): 삭제 상세 사유 (사유 분류는 ETC 고정)

        ## 응답 (AdminResponse)
        - **`bulkActionResults`**: List<BulkActionResult> — 각 itemId별 성공/실패 결과

        ## 동작
        - 물품 단위 독립 트랜잭션으로 처리되어 한 건 실패가 전체를 롤백하지 않는다.

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        - INVALID_REQUEST (400): itemIds 누락
        """
    )
    ResponseEntity<AdminResponse> bulkDeleteMemberItems(AdminRequest adminRequest, CustomUserDetails principal);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "관리자 → 회원 알림 발송 API 추가"
        )
    })
    @Operation(
        summary = "관리자 알림 발송 (개별 회원)",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수): 알림 수신 회원
        - **`adminNotificationTitle`** (String, 필수): 알림 제목
        - **`adminNotificationContent`** (String, 필수): 알림 본문
        - **`adminNotificationType`** (String, 선택): 알림 타입(NotificationType enum 이름)

        ## 응답 (AdminResponse)
        - 빈 응답 (HTTP 200 OK)

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        - INVALID_REQUEST (400): 제목/본문 누락
        """
    )
    ResponseEntity<AdminResponse> sendNotificationToMember(AdminRequest adminRequest, CustomUserDetails principal);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.29", author = Author.SUHSAECHAN, issueNumber = 814, description = "관리자 계정·처리자 본인 대상 변경 차단 (ADMIN_TARGET_FORBIDDEN, ADMIN_SELF_ACTION_FORBIDDEN 403)"),
    })
    ResponseEntity<AdminResponse> updateMemberStatus(AdminRequest request, CustomUserDetails principal);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.29", author = Author.SUHSAECHAN, issueNumber = 814, description = "관리자 계정·처리자 본인 정지 차단(403), 제재 이력에 처리 관리자(executorAdminId) 기록"),
    })
    ResponseEntity<AdminResponse> suspendMember(AdminRequest request, CustomUserDetails principal);

    ResponseEntity<AdminResponse> unsuspendMember(AdminRequest request);

    ResponseEntity<AdminResponse> getMemberSanctionHistory(AdminRequest request);

    ResponseEntity<AdminResponse> getAllSanctionHistory(AdminRequest request);
}
