package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.item.dto.TradeRequest;
import com.romrom.item.dto.TradeResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface TradeControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.01.20", author = Author.WISEUNGJAE, issueNumber = 452, description = "거래 요청 존재 여부 반환 API 구현"),
  })
  @Operation(
      summary = "거래 요청 존재 여부 반환",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (TradeRequest)
          - **`giveItemId`**: 교환 요청을 보낼 내 물품 Id (UUID)
          - **`takeItemId`**: 교환 요청을 받을 상대방 물품 Id (UUID)
          
          ## 반환값 (TradeResponse)
          - **`tradeRequestHistoryExists`**: 거래 요청 존재 여부 (boolean)
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          - **`INVALID_ITEM_OWNER`**: 요청을 보낸 물품의 소유주가 아닙니다.
          """
  )
  ResponseEntity<TradeResponse> validateTradeBeforeMessaging(
      CustomUserDetails customUserDetails,
      TradeRequest tradeRequest
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "차단된 회원에게 거래 요청을 방지하는 검증 로직 추가"),
      @ApiChangeLog(date = "2025.09.11", author = Author.KIMNAYOUNG, issueNumber = 301, description = "예외 처리 추가"),
      @ApiChangeLog(date = "2025.03.26", author = Author.KIMNAYOUNG, issueNumber = 74, description = "거래 요청"),
  })
  @Operation(
      summary = "거래 요청",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (TradeRequest)
          - **`member`**: 회원
          - **`takeItemId`**: 교환 요청을 받은 물품 Id (UUID)
          - **`giveItemId`**: 교환 요청을 보낸 물품 Id (UUID)
          - **`itemTradeOptions`**: 거래 옵션 (추가금, 직거래만, 택배거래만)
          
          ## 반환값 (Void)
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          - **`INVALID_ITEM_OWNER`**: 해당 물품의 소유주가 아닙니다.
          - **`TRADE_TO_SELF_FORBIDDEN`**: 자신의 물품에 거래 요청을 보낼 수 없습니다.
          - **`TRADE_ALREADY_PROCESSED`**: 거래가 불가능한 상태의 물품이 포함되어 있습니다.
          - **`ALREADY_REQUESTED_ITEM`**: 이미 거래 요청을 보낸 물품입니다.
          """
  )
  ResponseEntity<Void> requestTrade(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "차단된 회원과의 거래 요청 조회를 방지하는 검증 로직 추가"),
      @ApiChangeLog(date = "2025.09.29", author = Author.BAEKJIHOON, issueNumber = 331, description = "거래요청 상세 조회 및 isNew 태그 추가"),
  })
  @Operation(
      summary = "거래 요청 상세 조회",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (TradeRequest)
          - **`tradeRequestHistoryId`**: 거래 요청 Id (UUID)
          
          ## 반환값 (TradeResponse)
          TradeRequestHistory tradeRequestHistory
          ```
          """ + TradeApiDocsExamples.TRADE_REQUEST_HISTORY_DETAIL_RESPONSE_JSON + """
          ```
          
          ## 설명
          - tradeRequestHistoryId (ID)를 통해 거래요청 상세조회
          - 요청을 받은 사람이 거래요청 상세조회 호출 시 `isNew = false` 변경
          """
  )
  ResponseEntity<TradeResponse> getTradeRequestHistory(
      CustomUserDetails customUserDetails,
      TradeRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "차단된 회원과의 거래 요청 변경을 방지하는 검증 로직 추가"),
      @ApiChangeLog(date = "2025.09.11", author = Author.KIMNAYOUNG, issueNumber = 301, description = "거래 완료 상태로 변경"),
  })
  @Operation(
      summary = "거래 완료로 변경",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (TradeRequest)
          - **`member`**: 회원
          - **`tradeRequestHistoryId`**: 거래 요청 ID (UUID)
          
          ## 반환값 (Void)
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          - **`TRADE_REQUEST_NOT_FOUND`**: 해당 거래 요청이 존재하지 않습니다.
          - **`INVALID_ITEM_OWNER`**: 해당 물품의 소유주가 아닙니다.
          - **`TRADE_ALREADY_PROCESSED`**: 이미 처리(완료, 취소)된 거래 요청입니다.
          
          ## 설명
          - 거래 요청 완료 상태로 변경
          - 해당 물품 EXCHANGED 상태로 변경
          """
  )
  ResponseEntity<Void> acceptTradeRequest(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "차단된 회원과의 거래 요청 변경을 방지하는 검증 로직 추가"),
      @ApiChangeLog(date = "2025.09.11", author = Author.KIMNAYOUNG, issueNumber = 301, description = "예외 처리 추가"),
      @ApiChangeLog(date = "2025.04.03", author = Author.KIMNAYOUNG, issueNumber = 96, description = "받은/보낸 요청 멤버 검증 로직"),
      @ApiChangeLog(date = "2025.03.26", author = Author.KIMNAYOUNG, issueNumber = 74, description = "거래 요청 취소"),
  })
  @Operation(
      summary = "거래 요청 취소",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (TradeRequest)
          - **`member`**: 회원
          - **`tradeRequestHistoryId`**: 거래 요청 ID (UUID)
          
          ## 반환값 (Void)
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          - **`TRADE_REQUEST_NOT_FOUND`**: 해당하는 거래 요청이 존재하지 않습니다.
          - **`TRADE_ACCESS_FORBIDDEN`**: 거래 요청 권한이 없습니다.
          - **`TRADE_ALREADY_PROCESSED`**: 이미 처리(완료, 취소)된 거래 요청입니다.
          """
  )
  ResponseEntity<Void> cancelTradeRequest(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.10", author = Author.SUHSAECHAN, issueNumber = 497, description = "거래 요청 거절(물리 삭제) API 구현"),
  })
  @Operation(
      summary = "거래 요청 거절 (물리 삭제)",
      description = """
          ## 인증(JWT): **필요**

          ## 요청 파라미터 (TradeRequest)
          - **`tradeRequestHistoryId`**: 거래 요청 ID (UUID)

          ## 반환값 (Void)

          ## 에러코드
          - **`TRADE_REQUEST_NOT_FOUND`**: 해당 거래 요청이 존재하지 않습니다.
          - **`INVALID_ITEM_OWNER`**: 거래 요청을 받은 물품의 소유주가 아닙니다.
          - **`TRADE_ALREADY_PROCESSED`**: 이미 처리(완료, 취소, 채팅중)된 거래 요청입니다.

          ## 설명
          - 거래 요청을 받은 사람(takeItem 소유자)만 거절 가능
          - PENDING 상태에서만 거절 가능 (채팅이 시작된 경우 거절 불가)
          - 거절 시 DB에서 물리적으로 삭제 (상태 변경이 아닌 row 삭제)
          """
  )
  ResponseEntity<Void> rejectTradeRequest(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "차단된 회원과의 거래 요청 수정을 방지하는 검증 로직 추가"),
      @ApiChangeLog(date = "2025.09.11", author = Author.KIMNAYOUNG, issueNumber = 301, description = "거래 요청 수정"),
  })
  @Operation(
      summary = "거래 요청 수정",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (TradeRequest)
          - **`member`**: 회원
          - **`tradeRequestHistoryId`**: 거래 요청 ID (UUID)
          - **`itemTradeOptions`**: 거래 옵션 (추가금, 직거래만, 택배거래만)
          
          ## 반환값 (Void)
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          - **`INVALID_ITEM_OWNER`**: 요청을 보낸 물품의 소유주가 아닙니다.
          - **`TRADE_REQUEST_NOT_FOUND`**: 취소하려는 거래 요청이 존재하지 않습니다.
          - **`TRADE_ALREADY_PROCESSED`**: 이미 처리(완료, 취소)된 거래 요청입니다.
          """
  )
  ResponseEntity<Void> updateTradeRequest(
      CustomUserDetails customUserDetails,
      TradeRequest tradeRequest
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.04.30", author = Author.BAEKJIHOON, issueNumber = 667, description = "거래 요청 목록 정렬 기준(sortField) 및 정렬 방향(sortDirection) 파라미터 추가: CREATED_DATE/PRICE/AI_RECOMMENDED + ASC/DESC"),
      @ApiChangeLog(date = "2026.01.20", author = Author.WISEUNGJAE, issueNumber = 452, description = "받은 거래 요청 조회 시 tradeStatus 필터링 삭제"),
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "거래 요청 조회 시 차단된 회원과의 거래 요청 제외"),
      @ApiChangeLog(date = "2025.09.18", author = Author.BAEKJIHOON, issueNumber = 336, description = "응답값 구조 개선"),
      @ApiChangeLog(date = "2025.09.11", author = Author.KIMNAYOUNG, issueNumber = 301, description = "예외 처리 추가"),
      @ApiChangeLog(date = "2025.03.26", author = Author.KIMNAYOUNG, issueNumber = 74, description = "받은 거래 요청 조회"),
  })
  @Operation(
      summary = "받은 거래 요청 목록 조회",
      description = """
          ## 인증(JWT): **필요**

          ## 요청 파라미터 (TradeRequest)
          - **`takeItemId`**: 교환 요청을 받은 물품 Id (UUID)
          - **`pageNumber`**: 페이지 번호 (기본값 0)
          - **`pageSize`**: 페이지 크기 (기본값 30)
          - **`sortField`**: 정렬 기준 (선택, 기본값 `CREATED_DATE`)
            - **`CREATED_DATE`**: 거래 요청 생성일 기준 정렬
            - **`PRICE`**: 요청을 보낸 물품(`giveItem`) 가격 기준 정렬 (동일 가격 시 최신순 보조)
            - **`AI_RECOMMENDED`**: 조회자 본인의 선호 카테고리 임베딩과 요청을 보낸 물품(`giveItem`) 임베딩의 pgvector 유사도 정렬. 임베딩 없는 물품은 후순위 최신순 배치, 본인의 선호 임베딩 자체가 없으면 최신순 폴백. **이 정렬에서는 `sortDirection` 이 무시됩니다.**
          - **`sortDirection`**: 정렬 방향 (선택, 기본값 `DESC`)
            - **`DESC`**: 내림차순 (최신순 / 가격 높은 순)
            - **`ASC`**: 오름차순 (오래된 순 / 가격 낮은 순)
          
          ## 반환값 (TradeResponse)
          - **`Page<TradeRequestHistory>`**: TradeRequestHistory 객체 Page
          
          ## 반환값 예시
          ```
          """ + TradeApiDocsExamples.RECEIVED_TRADE_REQUESTS_RESPONSE_JSON + """
          ```
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          - **`INVALID_ITEM_OWNER`**: 요청을 보낸 물품의 소유주가 아닙니다.
          
          ## 설명
          - 물품의 소유주만 요청 가능합니다
          - TradeRequestHistory 하나의 객체 내부에 **요청을 받은 물품 (Item takeItem)** 정보, **요청을 보낸 물품 (Item giveItem)** 데이터가 포함됩니다
          - 각 물품(Item) 데이터 내부에는 물품 이미지(ItemImage)가 포함됩니다.
          """
  )
  ResponseEntity<TradeResponse> getReceivedTradeRequests(
      CustomUserDetails customUserDetails,
      TradeRequest tradeRequest
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.04.30", author = Author.BAEKJIHOON, issueNumber = 667, description = "거래 요청 목록 정렬 기준(sortField) 및 정렬 방향(sortDirection) 파라미터 추가: CREATED_DATE/PRICE/AI_RECOMMENDED + ASC/DESC"),
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "거래 요청 조회 시 차단된 회원과의 거래 요청 제외"),
      @ApiChangeLog(date = "2025.09.18", author = Author.BAEKJIHOON, issueNumber = 336, description = "응답값 구조 개선"),
      @ApiChangeLog(date = "2025.09.11", author = Author.KIMNAYOUNG, issueNumber = 301, description = "예외 처리 추가"),
      @ApiChangeLog(date = "2025.03.26", author = Author.KIMNAYOUNG, issueNumber = 74, description = "보낸 거래 요청 조회"),
  })
  @Operation(
      summary = "보낸 거래 요청 목록 조회",
      description = """
          ## 인증(JWT): **필요**

          ## 요청 파라미터 (TradeRequest)
          - **`giveItemId`**: 교환 요청을 보낸 물품 Id (UUID)
          - **`pageNumber`**: 페이지 번호 (기본값 0)
          - **`pageSize`**: 페이지 크기 (기본값 30)
          - **`sortField`**: 정렬 기준 (선택, 기본값 `CREATED_DATE`)
            - **`CREATED_DATE`**: 거래 요청 생성일 기준 정렬
            - **`PRICE`**: 요청을 받은 물품(`takeItem`) 가격 기준 정렬 (동일 가격 시 최신순 보조)
            - **`AI_RECOMMENDED`**: 조회자 본인의 선호 카테고리 임베딩과 요청을 받은 물품(`takeItem`) 임베딩의 pgvector 유사도 정렬. 임베딩 없는 물품은 후순위 최신순 배치, 본인의 선호 임베딩 자체가 없으면 최신순 폴백. **이 정렬에서는 `sortDirection` 이 무시됩니다.**
          - **`sortDirection`**: 정렬 방향 (선택, 기본값 `DESC`)
            - **`DESC`**: 내림차순 (최신순 / 가격 높은 순)
            - **`ASC`**: 오름차순 (오래된 순 / 가격 낮은 순)
          
          ## 반환값 (TradeResponse)
          - **`Page<TradeRequestHistory>`**: TradeRequestHistory 객체 Page
          
          ## 반환값 예시
          ```
          """ + TradeApiDocsSentRequestExamples.SENT_TRADE_REQUESTS_RESPONSE_JSON + """
          ```
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          - **`INVALID_ITEM_OWNER`**: 요청을 보낸 물품의 소유주가 아닙니다.
          
          ## 설명
          - giveItem이 거래 요청을 보낸 물품들을 조회
          - 해당 물품의 소유주만 요청 가능
          - TradeRequestHistory 하나의 객체 내부에 **요청을 받은 물품 (Item takeItem)** 정보, **요청을 보낸 물품 (Item giveItem)** 데이터가 포함됩니다
          """
  )
  ResponseEntity<TradeResponse> getSentTradeRequests(
      CustomUserDetails customUserDetails,
      TradeRequest tradeRequest
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.09.18", author = Author.BAEKJIHOON, issueNumber = 336, description = "응답값 구조 개선"),
      @ApiChangeLog(date = "2025.08.20", author = Author.WISEUNGJAE, issueNumber = 258, description = "item detail 조립 로직 N+1 문제 Fetch Join 및 ItemDetailAssembler 클래스로 해결"),
      @ApiChangeLog(date = "2025.07.12", author = Author.KIMNAYOUNG, issueNumber = 196, description = "거래 성사율 순으로 물품 정렬"),
  })
  @Operation(
      summary = "거래 성사율 높은 순으로 내 물품 정렬",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (TradeRequest)
          - **`takeItemId`**: 교환 요청을 받은 물품 Id (UUID)
          
          ## 반환값 (TradeResponse)
          - **`Page<Item> itemPage`: 물품 페이지네이션
          
          ## 반환값 예시
          ```
          """ + TradeApiDocsItemSortExamples.SORTED_TRADE_RATE_ITEMS_RESPONSE_JSON + """
          ```
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          - **`EMBEDDING_NOT_FOUND`**: 임베딩을 찾을 수 없습니다.
          """
  )
  ResponseEntity<TradeResponse> getSortedTradeRate(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.03.18", author = Author.KIMNAYOUNG, issueNumber = 590, description = "isRecommended 필드 추가"),
      @ApiChangeLog(date = "2026.02.26", author = Author.KIMNAYOUNG, issueNumber = 544, description = "거래 완료된 물품 제외"),
      @ApiChangeLog(date = "2026.02.26", author = Author.KIMNAYOUNG, issueNumber = 543, description = "선호 카테고리 임베딩 최신 1건만 조회"),
      @ApiChangeLog(date = "2026.01.28", author = Author.KIMNAYOUNG, issueNumber = 458, description = "내 물품 AI 추천 정렬"),
  })
  @Operation(
      summary = "물품 AI 추천 정렬",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (TradeRequest)
          - **`takeItemId`**: 교환 요청을 받은 물품 Id (UUID)
          
          ## 반환값 (TradeResponse)
          - **`Page<Item> itemPage`: 물품 페이지네이션
          
          ## 설명
          - 내 물품 중 어떤 물품이 상대방에게 가장 교환 성사율이 높은지 비교
          - takeItemId에 해당하는 물품의 소유자와 내 물품들 비교해 정렬
          - 상대방의 선호 카테고리 임베딩이 없을 경우 전체 물품 최신순으로 반환
          - 내 물품 중 임베딩이 없는 물품은 성사율 높은 순으로 정렬 후 뒤에 최신순으로 배치해 반환
          - 유사도 기반 추천하는 상위 N개 물품에 `isRecommended = true` 마킹
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          """
  )
  ResponseEntity<TradeResponse> getAiRecommendedItems(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

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

}
