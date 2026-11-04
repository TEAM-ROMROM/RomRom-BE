package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.item.dto.TradeRequest;
import com.romrom.item.dto.TradeResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

/**
 * 거래 후기 API 문서: 후기 작성, 받은 후기 목록, 받은 후기 통계 (TradeControllerDocs 분리, #858)
 */
public interface TradeReviewControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.04.06", author = Author.KIMNAYOUNG, issueNumber = 623, description = "거래 완료 후기 작성 " +
        "API 구현"),
  })
  @Operation(
      summary = "거래 완료 후기 작성",
      description = """
          ## 인증(JWT): **필요**

          ## 요청 파라미터 (TradeRequest)
          - **`tradeRequestHistoryId`**: 거래 요청 ID (UUID)
          - **`tradeReviewRating`**: 종합 평가 (BAD/GOOD/GREAT)
          - **`tradeReviewTags`**: 세부 항목 태그 목록 (복수 선택 가능)
            - FAST_RESPONSE, GOOD_ITEM_CONDITION, MATCHES_PHOTO, PUNCTUAL, KIND
          - **`reviewComment`**: 한마디

          ## 반환값
          - 없음 (200 OK)

          ## 에러코드
          - **`TRADE_REQUEST_NOT_FOUND`**: 거래 요청이 존재하지 않습니다.
          - **`TRADE_NOT_COMPLETED`**: 거래가 완료되지 않아 후기를 작성할 수 없습니다.
          - **`TRADE_REVIEW_ACCESS_FORBIDDEN`**: 해당 거래의 당사자만 후기를 작성할 수 있습니다.
          - **`TRADE_REVIEW_ALREADY_EXISTS`**: 이미 해당 거래에 후기를 작성했습니다.

          ## 설명
          - 거래 상태가 TRADED(거래 완료)인 경우에만 후기 작성 가능
          - 거래 당사자(takeItem 소유자 또는 giveItem 소유자) 각자 1회씩 작성 가능
          - 동일 거래에 대한 중복 후기 작성 방지
          """
  )
  ResponseEntity<Void> postTradeReview(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.05.08", author = Author.BAEKJIHOON, issueNumber = 684, description = "memberId 기반 받은 거래 후기 조회 API 구현"),
  })
  @Operation(
      summary = "거래 후기 조회",
      description = """
          ## 인증(JWT): **필요**

          ## 요청 파라미터 (TradeRequest)
          - **`memberId`**: 후기를 조회할 대상 회원 ID (UUID, 필수)
          - **`pageNumber`**: 페이지 번호 (기본값: 0)
          - **`pageSize`**: 페이지 크기 (기본값: 30)

          ## 반환값 (TradeResponse)
          - **`tradeReviewPage`**: 받은 거래 후기 페이지
            - `tradeReviewId`: 후기 ID (UUID)
            - `tradeReviewRating`: 종합 평가 (BAD/GOOD/GREAT)
            - `tradeReviewTags`: 세부 항목 태그 목록
            - `reviewComment`: 한마디 (최대 200자, 선택)
            - `reviewerMember`: 후기 작성자 회원 정보
            - `reviewedMember`: 후기 받은 회원 정보

          ## 에러코드
          - **`INVALID_REQUEST`**: memberId가 누락되었습니다.

          ## 설명
          - memberId를 기준으로 해당 회원이 받은 모든 거래 후기를 최신순으로 반환
          - 인증된 회원이라면 누구든지 타인의 후기 목록 조회 가능
          """
  )
  ResponseEntity<TradeResponse> getReceivedTradeReviews(
      CustomUserDetails customUserDetails,
      TradeRequest tradeRequest
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 858, description = "받은 거래 후기 통계 API 추가 (평균 평점·평점별·태그별 수, 블라인드 제외)"),
  })
  @Operation(
      summary = "받은 거래 후기 통계",
      description = """
          ## 인증(JWT): **필요**

          ## 요청 파라미터 (TradeRequest)
          - **`memberId`**: 통계를 조회할 대상 회원 ID (UUID, 필수)

          ## 반환값 (TradeResponse.tradeReviewStats)
          - **`totalReviewCount`**: 받은 후기 수
          - **`averageRatingScore`**: 평균 평점 (BAD 1, GOOD 2, GREAT 3, 소수 둘째 자리 반올림, 후기 없음 > null)
          - **`ratingCounts`**: 평점별 후기 수 (`{"BAD": 0, "GOOD": 3, "GREAT": 5}`, 모든 평점 포함)
          - **`tagCounts`**: 세부 태그별 선택 수 (모든 태그 포함)

          ## 설명
          - 관리자 블라인드 후기는 통계에서 제외 (목록 조회에서는 내용만 가려 자리 유지)
          - 전체 받은 후기 기준 (목록 페이지 크기와 무관)

          ## 에러코드
          - **`INVALID_REQUEST`**: memberId가 누락되었습니다.
          """
  )
  ResponseEntity<TradeResponse> getReceivedTradeReviewStats(
      CustomUserDetails customUserDetails,
      TradeRequest tradeRequest
  );
}
