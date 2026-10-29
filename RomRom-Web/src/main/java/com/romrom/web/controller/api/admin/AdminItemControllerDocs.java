package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminItemControllerDocs {

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.29", author = Author.SUHSAECHAN, issueNumber = 814, description = "잘못된 startDate/endDate 형식은 400(INVALID_REQUEST), 종료일은 해당 일자 23:59:59까지 포함"),
    })
    ResponseEntity<AdminResponse> getItems(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.24", author = Author.BAEKJIHOON, issueNumber = 715, description = "FK 제약 위반 방지: chat_room → trade_request_history cascade 삭제 순서 보장"),
    })
    @Operation(
        summary = "관리자 물품 삭제",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`itemId`** (UUID, 필수): 삭제할 물품 ID
        - **`itemAdminDeleteReason`** (ItemAdminDeleteReason, 필수): 삭제 사유
        - **`itemAdminDeleteDetail`** (String, 선택): 삭제 사유 상세 설명

        ## 동작 설명
        - FK 제약 위반 방지를 위해 cascade 순서 보장: chat_room → trade_request_history → item(soft delete)
        - 영향받는 회원에게 FCM 삭제 알림 발송

        ## 에러코드
        - ITEM_NOT_FOUND (404): 해당 itemId의 물품이 존재하지 않음
        - INVALID_REQUEST (400): 삭제 사유가 누락됨
        """
    )
    ResponseEntity<Void> deleteItem(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.11", author = Author.KIMNAYOUNG, issueNumber = 686, description = "관리자 물품 거래 상태 변경 API" +
          " 추가"),
    })
    @Operation(
        summary = "관리자 물품 거래 상태 변경",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`itemId`** (UUID, 필수): 거래 상태를 변경할 물품 ID
        - **`itemStatus`** (ItemStatus, 필수): 변경할 거래 상태 (AVAILABLE / EXCHANGED)

        ## 동작 설명
        - 해당 itemId의 물품 거래 상태를 요청한 itemStatus로 변경합니다.

        ## 에러코드
        - ITEM_NOT_FOUND (404): 해당 itemId의 물품이 존재하지 않음
        """
    )
    ResponseEntity<Void> updateItemStatus(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.04", author = Author.SUHSAECHAN, issueNumber = 712, description = "관리자 화면이 노출 차단 상태를 읽지 못하던 문제 수정: Item.isAdminHidden 의 @JsonIgnore 제거(응답 노출), adminHideReason 은 @JsonIgnore 유지하고 itemDetail 에 별도 노출"),
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 712, description = "관리자 물품 상세 조회 API 추가"),
    })
    @Operation(
        summary = "관리자 물품 상세 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`itemId`** (UUID, 필수): 조회할 물품 ID

        ## 반환값 (AdminResponse.itemDetail)
        - **`item`**: 물품 기본 정보 (이미지 목록 + `isAdminHidden` 노출 차단 여부 포함)
        - **`adminHideReason`**: 관리자 노출 차단 사유 (내부용, Item 에는 @JsonIgnore 라 별도 노출)
        - **`tradeHistories`**: 해당 물품이 포함된 거래 이력 목록
        - **`itemReports`**: 해당 물품에 대한 신고 이력 목록

        ## 에러코드
        - ITEM_NOT_FOUND (404): 해당 itemId의 물품이 존재하지 않음
        """
    )
    ResponseEntity<AdminResponse> getItemDetail(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 712, description = "관리자 물품 카테고리/가격 수정 API 추가"),
    })
    @Operation(
        summary = "관리자 물품 카테고리/가격 수정",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`itemId`** (UUID, 필수): 수정할 물품 ID
        - **`itemName`** (String, 선택): 변경할 물품명
        - **`itemDescription`** (String, 선택): 변경할 물품 설명
        - **`itemCategory`** (ItemCategory, 선택): 변경할 카테고리
        - **`itemCondition`** (ItemCondition, 선택): 변경할 물품 상태
        - **`price`** (Integer, 선택): 변경할 가격

        ## 에러코드
        - ITEM_NOT_FOUND (404): 해당 itemId의 물품이 존재하지 않음
        """
    )
    ResponseEntity<Void> updateItem(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 712, description = "관리자 물품 노출 차단 API 추가"),
    })
    @Operation(
        summary = "관리자 물품 노출 차단",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`itemId`** (UUID, 필수): 노출 차단할 물품 ID
        - **`adminHideReason`** (String, 선택): 차단 사유 (내부용)

        ## 동작 설명
        - 실데이터 삭제 없이 일반 사용자 조회/검색에서 해당 물품을 제외합니다.

        ## 에러코드
        - ITEM_NOT_FOUND (404): 해당 itemId의 물품이 존재하지 않음
        """
    )
    ResponseEntity<Void> hideItem(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 712, description = "관리자 물품 노출 차단 해제 API 추가"),
    })
    @Operation(
        summary = "관리자 물품 노출 차단 해제",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`itemId`** (UUID, 필수): 노출 차단을 해제할 물품 ID

        ## 에러코드
        - ITEM_NOT_FOUND (404): 해당 itemId의 물품이 존재하지 않음
        """
    )
    ResponseEntity<Void> unhideItem(AdminRequest request);
}
