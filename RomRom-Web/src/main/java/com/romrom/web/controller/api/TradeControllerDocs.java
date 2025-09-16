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
      @ApiChangeLog(
          date = "2025.09.11",
          author = Author.KIMNAYOUNG,
          issueNumber = 301,
          description = "예외 처리 추가"
      ),
      @ApiChangeLog(
          date = "2025.03.26",
          author = Author.KIMNAYOUNG,
          issueNumber = 74,
          description = "거래 요청"
      )
  })
  @Operation(
      summary = "거래 요청",
      description = """
      ## 인증(JWT): **필요**

      ## 요청 파라미터 (TradeRequest)
      - **`member`**: 회원
      - **`takeItemId`**: 교환 요청을 받은 물품 Id (UUID)
      - **`giveItemId`**: 교환 요청을 보낸 물품 Id (UUID)
      - **`tradeOptions`**: 거래 옵션 (추가금, 직거래만, 택배거래만)

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
      @ApiChangeLog(
          date = "2025.09.11",
          author = Author.KIMNAYOUNG,
          issueNumber = 301,
          description = "거래 완료 상태로 변경"
      )
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
      @ApiChangeLog(
          date = "2025.09.11",
          author = Author.KIMNAYOUNG,
          issueNumber = 301,
          description = "예외 처리 추가"
      ),
      @ApiChangeLog(
          date = "2025.04.03",
          author = Author.KIMNAYOUNG,
          issueNumber = 96,
          description = "받은/보낸 요청 멤버 검증 로직"
      ),
      @ApiChangeLog(
          date = "2025.03.26",
          author = Author.KIMNAYOUNG,
          issueNumber = 74,
          description = "거래 요청 취소"
      )
  })
  @Operation(
      summary = "거래 요청 취소",
      description = """
      ## 인증(JWT): **필요**

      ## 요청 파라미터 (TradeRequest)
      - **`member`**: 회원
      - **`tradeRequestHistoryId`**: 거래 요청 ID (UUID)
      - **`tradeOptions`**: 거래 옵션 (추가금, 직거래만, 택배거래만)

      ## 반환값 (Void)

      ## 에러코드
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      - **`TRADE_REQUEST_NOT_FOUND`**: 해당하는 거래 요청이 존재하지 않습니다.
      - **`TRADE_ACCESS_FORBIDDEN`**: 거래 요청 권한이 없습니다.
      - **`TRADE_ALREADY_PROCESSED`**: 이미 처리(완료, 취소)된 거래 요청입니다.
      
      ## 설명
      - 거래 옵션 수정
      """
  )
  ResponseEntity<Void> cancelTradeRequest(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.09.11",
          author = Author.KIMNAYOUNG,
          issueNumber = 301,
          description = "거래 요청 수정"
      ),
  })
  @Operation(
      summary = "거래 요청 수정",
      description = """
      ## 인증(JWT): **필요**

      ## 요청 파라미터 (TradeRequest)
      - **`member`**: 회원
      - **`tradeRequestHistoryId`**: 거래 요청 ID (UUID)
      - **`tradeOptions`**: 거래 옵션 (추가금, 직거래만, 택배거래만)

      ## 반환값 (Void)

      ## 에러코드
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      - **`INVALID_ITEM_OWNER`**: 요청을 보낸 물품의 소유주가 아닙니다.
      - **`TRADE_REQUEST_NOT_FOUND`**: 취소하려는 거래 요청이 존재하지 않습니다.
      - **`TRADE_ALREADY_PROCESSED`**: 이미 처리(완료, 취소)된 거래 요청입니다.
      """
  )
  ResponseEntity<Void> updateTradeRequest(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.09.11",
          author = Author.KIMNAYOUNG,
          issueNumber = 301,
          description = "예외 처리 추가"
      ),
      @ApiChangeLog(
          date = "2025.03.26",
          author = Author.KIMNAYOUNG,
          issueNumber = 74,
          description = "받은 거래 요청 조회"
      )
  })
  @Operation(
      summary = "받은 거래 요청 목록 조회",
      description = """
      ## 인증(JWT): **필요**

      ## 요청 파라미터 (TradeRequest)
      - **`member`**: 회원
      - **`takeItemId`**: 교환 요청을 받은 물품 Id (UUID)

      ## 반환값 (Page<TradeResponse>)
      - **`item`**: 교환 상대의 물품
      - **`itemImages`**: 물품 이미지 리스트
      - **`tradeOptions`**: 거래 옵션

      ## 에러코드
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      - **`INVALID_ITEM_OWNER`**: 요청을 보낸 물품의 소유주가 아닙니다.
      """
  )
  ResponseEntity<TradeResponse> getReceivedTradeRequests(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.09.11",
          author = Author.KIMNAYOUNG,
          issueNumber = 301,
          description = "예외 처리 추가"
      ),
      @ApiChangeLog(
          date = "2025.03.26",
          author = Author.KIMNAYOUNG,
          issueNumber = 74,
          description = "보낸 거래 요청 조회"
      )
  })
  @Operation(
      summary = "보낸 거래 요청 목록 조회",
      description = """
      ## 인증(JWT): **필요**

      ## 요청 파라미터 (TradeRequest)
      - **`member`**: 회원
      - **`giveItemId`**: 교환 요청을 보낸 물품 Id (UUID)

      ## 반환값 (Page<TradeResponse>)
      - **`item`**: 교환 상대의 물품
      - **`itemImages`**: 물품 이미지 리스트
      - **`tradeOptions`**: 거래 옵션

      ## 에러코드
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      - **`INVALID_ITEM_OWNER`**: 요청을 보낸 물품의 소유주가 아닙니다.
      """
  )
  ResponseEntity<TradeResponse> getSentTradeRequests(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.08.20",
          author = Author.WISEUNGJAE,
          issueNumber = 258,
          description = "item detail 조립 로직 N+1 문제 Fetch Join 및 ItemDetailAssembler 클래스로 해결"
      ),
      @ApiChangeLog(
          date = "2025.07.12",
          author = Author.KIMNAYOUNG,
          issueNumber = 196,
          description = "거래 성사율 순으로 물품 정렬"
      )
  })
  @Operation(
      summary = "거래 성사율 높은 순으로 내 물품 정렬",
      description = """
      ## 인증(JWT): **필요**

      ## 요청 파라미터 (TradeRequest)
      - **`takeItemId`**: 교환 요청을 받은 물품 Id (UUID)

      ## 반환값 (TradeResponse)
      - **`Page<ItemDetail>`**: 정렬된 물품 리스트

      ## 에러코드
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      - **`EMBEDDING_NOT_FOUND`**: 임베딩을 찾을 수 없습니다.
      """
  )
  ResponseEntity<TradeResponse> getSortedTradeRate(CustomUserDetails customUserDetails, TradeRequest tradeRequest);
}
