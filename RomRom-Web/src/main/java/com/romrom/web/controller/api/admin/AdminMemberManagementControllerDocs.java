package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminMemberManagementControllerDocs {

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 838, description = "회원 차단 관계 조회 API 추가"),
    })
    @Operation(
        summary = "회원 차단 관계 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수): 대상 회원

        ## 반환값 (AdminResponse)
        - **`memberBlocks`**: 대상 회원이 차단한 관계 + 대상 회원을 차단한 관계 (최신순, 양쪽 회원 포함)

        ## 에러코드
        - INVALID_REQUEST (400): memberId 누락
        """
    )
    ResponseEntity<AdminResponse> getMemberBlocks(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 838, description = "회원 차단 관계 해제 API 추가"),
    })
    @Operation(
        summary = "회원 차단 관계 해제",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberBlockId`** (UUID, 필수): 해제할 차단 관계

        ## 반환값
        - 빈 AdminResponse (HTTP 200)

        ## 에러코드
        - INVALID_REQUEST (400): memberBlockId 누락 또는 없는 관계
        """
    )
    ResponseEntity<AdminResponse> deleteMemberBlock(AdminRequest request, CustomUserDetails principal);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 838, description = "회원 기기별 FCM 토큰 조회 API 추가"),
    })
    @Operation(
        summary = "회원 FCM 토큰 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수): 대상 회원

        ## 반환값 (AdminResponse)
        - **`fcmTokens`**: 기기별 토큰 (deviceType, token, 등록·갱신 일시, 최근 갱신순)

        ## 동작 설명
        - 푸시 미수신 문의 확인용. 토큰이 없으면 앱이 아직 등록하지 않았거나 로그아웃 상태입니다.
        """
    )
    ResponseEntity<AdminResponse> getMemberFcmTokens(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 838, description = "회원 FCM 토큰 삭제 API 추가"),
    })
    @Operation(
        summary = "회원 FCM 토큰 삭제",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`fcmTokenId`** (UUID, 필수): 삭제할 토큰

        ## 동작 설명
        - 만료·잘못된 토큰 정리용. 앱을 다시 실행하면 새 토큰이 등록됩니다.

        ## 에러코드
        - INVALID_REQUEST (400): fcmTokenId 누락 또는 없는 토큰
        """
    )
    ResponseEntity<AdminResponse> deleteFcmToken(AdminRequest request, CustomUserDetails principal);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 838, description = "관리자 회원 프로필(닉네임·사진) 수정 API 추가"),
    })
    @Operation(
        summary = "회원 프로필 수정",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수): 대상 회원
        - **`memberNickname`** (String, 선택): 새 닉네임 (20자 이하, 공백이면 변경 안 함)
        - **`isProfileImageReset`** (Boolean, 선택): true면 프로필 사진을 기본 이미지로 초기화

        ## 반환값 (AdminResponse)
        - **`member`**: 변경된 회원

        ## 에러코드
        - INVALID_REQUEST (400): memberId 누락, 닉네임 20자 초과
        - DUPLICATE_NICKNAME (400): 다른 회원이 사용 중인 닉네임
        - ADMIN_TARGET_FORBIDDEN (403): 관리자 계정 대상
        - ADMIN_SELF_ACTION_FORBIDDEN (403): 본인 계정 대상
        """
    )
    ResponseEntity<AdminResponse> updateMemberProfile(AdminRequest request, CustomUserDetails principal);
}
