package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminReviewControllerDocs {

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.04", author = Author.SUHSAECHAN, issueNumber = 771, description = "관리자 후기 목록 조회 API 추가 (평점/기간/블라인드여부 필터)"),
    })
    @Operation(
        summary = "관리자 후기 목록 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data, 모두 선택)
        - **`tradeReviewRating`** (TradeReviewRating): 평점 필터 (BAD/GOOD/GREAT, 미입력=전체)
        - **`isBlindedFilter`** (Boolean): 블라인드 여부 필터 (true=블라인드만, false=정상만, 미입력=전체)
        - **`startDate`** / **`endDate`** (String, yyyy-MM-dd): 작성일 기간 필터
        - **`pageNumber`** / **`pageSize`** / **`sortBy`** / **`sortDirection`**: 페이지네이션

        ## 반환값 (AdminResponse)
        - **`reviews`**: 페이지네이션된 후기 목록 (작성자/대상자/거래 + blindInfo 포함)
        - **`totalCount`** / **`totalPages`** / **`totalElements`** / **`currentPage`**: 페이지 정보
        """
    )
    ResponseEntity<AdminResponse> getReviews(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.04", author = Author.SUHSAECHAN, issueNumber = 771, description = "관리자 후기 블라인드 처리 API 추가 (처리자/시각 기록)"),
    })
    @Operation(
        summary = "관리자 후기 블라인드 처리",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`tradeReviewId`** (UUID, 필수): 블라인드 처리할 후기 ID
        - **`blindReason`** (String, 선택): 블라인드 사유

        ## 동작 설명
        - 실데이터 삭제 없이 일반 사용자 조회에서 후기 내용을 "관리자에 의해 블라인드 처리된 후기입니다"로 치환합니다.
        - 처리한 관리자(blindByAdminId)와 처리 시각(blindDate)을 기록합니다.

        ## 에러코드
        - TRADE_REVIEW_NOT_FOUND (404): 해당 후기가 존재하지 않음
        """
    )
    ResponseEntity<AdminResponse> blindReview(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.04", author = Author.SUHSAECHAN, issueNumber = 771, description = "관리자 후기 블라인드 해제 API 추가"),
    })
    @Operation(
        summary = "관리자 후기 블라인드 해제",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`tradeReviewId`** (UUID, 필수): 블라인드를 해제할 후기 ID

        ## 동작 설명
        - 블라인드 처리 정보(isBlinded/사유/처리자/시각)를 초기화하여 후기를 다시 노출합니다.

        ## 에러코드
        - TRADE_REVIEW_NOT_FOUND (404): 해당 후기가 존재하지 않음
        """
    )
    ResponseEntity<AdminResponse> unblindReview(AdminRequest request);
}
