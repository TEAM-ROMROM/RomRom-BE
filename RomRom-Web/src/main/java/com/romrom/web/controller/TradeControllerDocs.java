package com.romrom.web.controller;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.item.dto.TradeRequest;
import com.romrom.item.dto.TradeResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

public interface TradeControllerDocs {

  @ApiChangeLogs({
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
      - **`ALREADY_REQUESTED_ITEM`**: 이미 거래 요청을 보낸 물품입니다.
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      """
  )
  ResponseEntity<Void> requestTrade(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
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
      - **`takeItemId`**: 교환 요청을 받은 물품 Id (UUID)
      - **`giveItemId`**: 교환 요청을 보낸 물품 Id (UUID)
      - **`tradeOptions`**: 거래 옵션 (추가금, 직거래만, 택배거래만)

      ## 반환값 (Void)

      ## 에러코드
      - **`TRADE_REQUEST_NOT_FOUND`**: 취소하려는 거래 요청이 존재하지 않습니다.
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      - **`TRADE_CANCEL_FORBIDDEN`**: 거래 요청을 취소할 수 있는 권한이 없습니다.
      """
  )
  ResponseEntity<Void> cancelTradeRequest(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
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
      """
  )
  ResponseEntity<Page<TradeResponse>> getReceivedTradeRequests(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
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
      """
  )
  ResponseEntity<Page<TradeResponse>> getSentTradeRequests(CustomUserDetails customUserDetails, TradeRequest tradeRequest);
}
