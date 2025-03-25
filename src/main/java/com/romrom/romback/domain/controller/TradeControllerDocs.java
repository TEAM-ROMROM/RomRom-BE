package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.constant.Author;
import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.dto.TradeRequest;
import com.romrom.romback.domain.object.dto.TradeResponse;
import com.romrom.romback.global.docs.ApiChangeLog;
import com.romrom.romback.global.docs.ApiChangeLogs;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.http.ResponseEntity;

public interface TradeControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.03.26",
          author = Author.KIMNAYOUNG,
          issueNumber = 74,
          description = "거래 요청, 거래 요청 취소, 받은 요청/보낸 요청 조회 API 문서화"
      )
  })
  @Operation(
      summary = "거래 요청",
      description = """
      ## 인증(JWT): **필요**

      ## 요청 파라미터 (TradeRequest)
      - **`member`**: 회원
      - **`requestedItemId`**: 교환 요청을 받은 물품 Id (UUID)
      - **`requestingItemId`**: 교환 요청을 보낸 물품 Id (UUID)
      - **`tradeOptions`**: 상품 옵션 (추가금, 직거래만, 택배거래만)

      ## 반환값 (Void)
      
      ## 에러코드
      - **`ALREADY_REQUESTED_ITEM`**: 이미 거래 요청을 보낸 물품입니다.
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      """
  )
  ResponseEntity<Void> requestTrade(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.03.26",
          author = Author.KIMNAYOUNG,
          issueNumber = 74,
          description = "거래 요청 취소 API 문서화"
      )
  })
  @Operation(
      summary = "거래 요청 취소",
      description = """
      ## 인증(JWT): **필요**

      ## 요청 파라미터 (TradeRequest)
      - **`member`**: 회원
      - **`requestedItemId`**: 교환 요청을 받은 물품 Id (UUID)
      - **`requestingItemId`**: 교환 요청을 보낸 물품 Id (UUID)
      - **`tradeOptions`**: 상품 옵션 (추가금, 직거래만, 택배거래만)

      ## 반환값 (Void)

      ## 에러코드
      - **`TRADE_REQUEST_NOT_FOUND`**: 취소하려는 거래 요청이 존재하지 않습니다.
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      """
  )
  ResponseEntity<Void> cancelTradeRequest(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.03.26",
          author = Author.KIMNAYOUNG,
          issueNumber = 74,
          description = "받은 거래 요청 조회 API 문서화"
      )
  })
  @Operation(
      summary = "받은 거래 요청 목록 조회",
      description = """
      ## 인증(JWT): **필요**

      ## 요청 파라미터 (TradeRequest)
      - **`member`**: 회원
      - **`requestedItemId`**: 교환 요청을 받은 물품 Id (UUID)

      ## 반환값 (List<TradeResponse>)
      - **`item`**: 교환 상대의 물품
      - **`itemImages`**: 물품 이미지 리스트
      - **`tradeOptions`**: 상품 옵션

      ## 에러코드
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      """
  )
  ResponseEntity<List<TradeResponse>> getReceivedTradeRequests(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.03.26",
          author = Author.KIMNAYOUNG,
          issueNumber = 74,
          description = "보낸 거래 요청 조회 API 문서화"
      )
  })
  @Operation(
      summary = "보낸 거래 요청 목록 조회",
      description = """
      ## 인증(JWT): **필요**

      ## 요청 파라미터 (TradeRequest)
      - **`member`**: 회원
      - **`requestingItemId`**: 교환 요청을 보낸 물품 Id (UUID)

      ## 반환값 (List<TradeResponse>)
      - **`item`**: 교환 상대의 물품
      - **`itemImages`**: 물품 이미지 리스트
      - **`tradeOptions`**: 상품 옵션

      ## 에러코드
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      """
  )
  ResponseEntity<List<TradeResponse>> getSentTradeRequests(CustomUserDetails customUserDetails, TradeRequest tradeRequest);
}
