package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminTradeControllerDocs {

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.29", author = Author.SUHSAECHAN, issueNumber = 814, description = "잘못된 날짜 형식 400(INVALID_REQUEST), 종료일 당일 거래 누락 수정 (23:59:59까지 포함)"),
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 710, description = "관리자 거래 목록 조회 API 추가"),
    })
    @Operation(
        summary = "관리자 거래 목록 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`tradeStatus`** (TradeStatus, 선택): 거래 상태 필터 (PENDING/CHATTING/TRADE_COMPLETE_REQUESTED/TRADED/CANCELED, null이면 전체)
        - **`searchKeyword`** (String, 선택): takeItem/giveItem 물품명 또는 양쪽 회원 닉네임 검색
        - **`startDate`** (String, 선택): 등록일 시작 (yyyy-MM-dd)
        - **`endDate`** (String, 선택): 등록일 종료 (yyyy-MM-dd, 당일 포함)
        - **`pageNumber`** (Integer, 선택, 기본값 0): 페이지 번호
        - **`pageSize`** (Integer, 선택, 기본값 20): 페이지 크기
        - **`sortBy`** (String, 선택, 기본값 createdDate): 정렬 필드
        - **`sortDirection`** (Sort.Direction, 선택, 기본값 DESC): 정렬 방향

        ## 에러코드
        - INVALID_REQUEST (400): 날짜 형식 오류

        ## 반환값 (AdminResponse)
        - **`trades`**: 페이지네이션된 거래 이력 목록 (takeItem/giveItem/회원 정보 포함)
        - **`totalCount`**: 전체 거래 건수
        - **`totalPages`**: 전체 페이지 수
        - **`totalElements`**: 전체 요소 수
        - **`currentPage`**: 현재 페이지
        """
    )
    ResponseEntity<AdminResponse> getTrades(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.05", author = Author.SUHSAECHAN, issueNumber = 710, description = "거래 상세 응답에 채팅 전체 내역(chatMessages) 추가 - 관리자 분쟁/신고 추적용"),
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 710, description = "관리자 거래 상세 조회 API 추가"),
    })
    @Operation(
        summary = "관리자 거래 상세 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`tradeRequestHistoryId`** (UUID, 필수): 조회할 거래 이력 ID

        ## 반환값 (AdminResponse.tradeDetail)
        - **`tradeRequestHistory`**: 거래 이력 (takeItem/giveItem 및 각 소유 회원 정보 포함, 각 물품의 itemImages 포함)
        - **`chatRoom`**: 연결된 채팅방 (CHATTING 이상 상태에서만 존재, PENDING이면 null)
        - **`chatMessages`**: 거래 채팅 전체 내역 (시간순 오름차순, 채팅방 없으면 빈 리스트). senderId/recipientId/content/imageUrls/type/createdDate 포함

        ## 에러코드
        - TRADE_REQUEST_NOT_FOUND (404): 해당 거래 이력이 존재하지 않음
        """
    )
    ResponseEntity<AdminResponse> getTradeDetail(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 710, description = "관리자 거래 강제 취소 API 추가"),
    })
    @Operation(
        summary = "관리자 거래 강제 취소",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`tradeRequestHistoryId`** (UUID, 필수): 강제 취소할 거래 이력 ID
        - **`adminTradeForceReason`** (String, 선택): 강제 취소 사유 (내부용)

        ## 동작 설명
        - PENDING/CHATTING/TRADE_COMPLETE_REQUESTED 상태의 거래를 CANCELED로 변경합니다.
        - 기존 cancelTradeRequest 흐름과 동일하게 상태 변경만 수행합니다.

        ## 에러코드
        - TRADE_REQUEST_NOT_FOUND (404): 해당 거래 이력이 존재하지 않음
        - TRADE_ALREADY_COMPLETED (409): 이미 완료된 거래 (TRADED 상태)
        """
    )
    ResponseEntity<Void> forceTradeCancel(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 710, description = "관리자 거래 강제 완료 API 추가"),
    })
    @Operation(
        summary = "관리자 거래 강제 완료",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`tradeRequestHistoryId`** (UUID, 필수): 강제 완료할 거래 이력 ID
        - **`adminTradeForceReason`** (String, 선택): 강제 완료 사유 (내부용)

        ## 동작 설명
        - CHATTING/TRADE_COMPLETE_REQUESTED 상태의 거래를 TRADED로 변경합니다.
        - 양쪽 물품(takeItem/giveItem) 상태를 EXCHANGED로 처리합니다.
        - 채팅방이 있으면 "운영자에 의해 교환 완료 처리되었습니다." 시스템 메시지를 전송합니다.

        ## 에러코드
        - TRADE_REQUEST_NOT_FOUND (404): 해당 거래 이력이 존재하지 않음
        - TRADE_ALREADY_COMPLETED (409): 이미 완료된 거래 (TRADED 상태)
        - INVALID_REQUEST (400): PENDING 상태 거래 (채팅방 없음)
        """
    )
    ResponseEntity<Void> forceTradeComplete(AdminRequest request);
}
