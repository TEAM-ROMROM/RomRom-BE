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
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 858, description = "동네 인증 스위치 켜짐 시 미인증 회원 403 LOCATION_NOT_VERIFIED"),
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 859, description = "추가금(extraChargeAmount)·한 줄 메시지(requestMessage) 선택 파라미터 추가"),
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
          - **`extraChargeAmount`** (선택): 추가금 (원, 0~10,000,000). `EXTRA_CHARGE` 옵션을 선택했을 때만 저장, 아니면 무시
          - **`requestMessage`** (선택): 한 줄 메시지 (앞뒤 공백 제거 후 최대 100자, 줄바꿈·제어문자 불가, 비속어 필터 적용, 빈 값은 null)
          
          ## 반환값 (Void)
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          - **`INVALID_ITEM_OWNER`**: 해당 물품의 소유주가 아닙니다.
          - **`INVALID_TRADE_EXTRA_CHARGE_AMOUNT`**: 추가금이 0~10,000,000원 범위를 벗어났습니다.
          - **`INVALID_TRADE_REQUEST_MESSAGE`**: 한 줄 메시지가 100자를 넘거나 제어문자를 포함합니다.
          - **`PROHIBITED_CONTENT`**: 한 줄 메시지에 부적절한 표현이 포함되어 있습니다.
          - **`TRADE_TO_SELF_FORBIDDEN`**: 자신의 물품에 거래 요청을 보낼 수 없습니다.
          - **`TRADE_ALREADY_PROCESSED`**: 거래가 불가능한 상태의 물품이 포함되어 있습니다.
          - **`ALREADY_REQUESTED_ITEM`**: 이미 거래 요청을 보낸 물품입니다.
          - **`LOCATION_NOT_VERIFIED`** (403): 동네 인증 필요 (system_config `location.verification.enforced` = true 일 때만, 기본 꺼짐)
          """
  )
  ResponseEntity<Void> requestTrade(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 858, description = "isReviewWrittenByMe(내 후기 작성 여부), tradeRequestHistory.chatRoomId 추가"),
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 859, description = "응답에 extraChargeAmount·requestMessage·checkedAt·rejectedAt 추가, 받은 사람 최초 조회 시 checkedAt 기록"),
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
          
          - **`isReviewWrittenByMe`**: 내가 이 거래 후기를 썼는지 (boolean)
          - **`tradeRequestHistory.chatRoomId`**: 이 요청의 채팅방 ID (방 없음·삭제 대기 > null)

          ## 설명
          - tradeRequestHistoryId (ID)를 통해 거래요청 상세조회
          - 요청을 받은 사람이 거래요청 상세조회 호출 시 `isNew = false` 변경 + 최초 1회 `checkedAt` 기록 (보낸 쪽 '확인함' 표시용)
          - 추가 응답 필드 (#859, 값이 없으면 null)
            - **`extraChargeAmount`**: 추가금 (원)
            - **`requestMessage`**: 한 줄 메시지
            - **`checkedAt`**: 받은 사람이 처음 확인한 시각
            - **`rejectedAt`**: 거절 시각 (`tradeStatus = REJECTED`일 때)
          - 물품(`takeItem`·`giveItem`)에 **`isReserved`** (예약중 여부) 포함
          """
  )
  ResponseEntity<TradeResponse> getTradeRequestHistory(
      CustomUserDetails customUserDetails,
      TradeRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 859, description = "거래 완료 시 두 물품의 남은 대기(PENDING) 요청 CANCELED 처리 + 예약 해제"),
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
          - 두 물품이 포함된 다른 대기(PENDING) 요청은 CANCELED 처리 (채팅 완료 확정·관리자 강제 완료도 동일)
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
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 859, description = "거절 시 물리 삭제 대신 REJECTED 상태 + rejectedAt 보존"),
      @ApiChangeLog(date = "2026.02.10", author = Author.SUHSAECHAN, issueNumber = 497, description = "거래 요청 거절(물리 삭제) API 구현"),
  })
  @Operation(
      summary = "거래 요청 거절",
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
          - 거절 시 행을 보존하고 `tradeStatus = REJECTED`, `rejectedAt` 기록 (#859)
          - REJECTED 건은 받은 요청 목록에서 제외, 보낸 요청 목록에서는 `includeRejected=true`일 때만 포함
          - 같은 물품 쌍으로 다시 요청 가능 (중복 요청 검사 대상 아님)
          """
  )
  ResponseEntity<Void> rejectTradeRequest(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 859, description = "추가금(extraChargeAmount)·한 줄 메시지(requestMessage) 수정 지원"),
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
          - **`extraChargeAmount`** (선택): 추가금. 미전송 시 기존 값 유지, `EXTRA_CHARGE` 해제 시 삭제
          - **`requestMessage`** (선택): 한 줄 메시지. 미전송 시 기존 값 유지, 공백만 보내면 삭제
          
          ## 반환값 (Void)
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          - **`INVALID_ITEM_OWNER`**: 요청을 보낸 물품의 소유주가 아닙니다.
          - **`INVALID_TRADE_EXTRA_CHARGE_AMOUNT`**: 추가금이 0~10,000,000원 범위를 벗어났습니다.
          - **`INVALID_TRADE_REQUEST_MESSAGE`**: 한 줄 메시지가 100자를 넘거나 제어문자를 포함합니다.
          - **`PROHIBITED_CONTENT`**: 한 줄 메시지에 부적절한 표현이 포함되어 있습니다.
          - **`TRADE_REQUEST_NOT_FOUND`**: 취소하려는 거래 요청이 존재하지 않습니다.
          - **`TRADE_ALREADY_PROCESSED`**: 이미 처리(완료, 취소)된 거래 요청입니다.
          """
  )
  ResponseEntity<Void> updateTradeRequest(
      CustomUserDetails customUserDetails,
      TradeRequest tradeRequest
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 858, description = "각 TradeRequestHistory 에 chatRoomId 추가 (채팅방 일괄 조회)"),
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 859, description = "거절(REJECTED) 건 제외, 응답에 extraChargeAmount·requestMessage 추가"),
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
            - **`chatRoomId`**: 이 요청의 채팅방 ID (방 없음·삭제 대기 > null)
          
          ## 반환값 예시
          ```
          """ + TradeApiDocsExamples.RECEIVED_TRADE_REQUESTS_RESPONSE_JSON + """
          ```
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          - **`INVALID_ITEM_OWNER`**: 요청을 보낸 물품의 소유주가 아닙니다.
          
          ## 설명
          - 물품의 소유주만 요청 가능합니다
          - 거절(REJECTED)·취소(CANCELED) 건은 포함하지 않습니다
          - 각 요청에 `extraChargeAmount`(추가금)·`requestMessage`(한 줄 메시지) 포함 (없으면 null)
          - TradeRequestHistory 하나의 객체 내부에 **요청을 받은 물품 (Item takeItem)** 정보, **요청을 보낸 물품 (Item giveItem)** 데이터가 포함됩니다
          - 각 물품(Item) 데이터 내부에는 물품 이미지(ItemImage)가 포함됩니다.
          """
  )
  ResponseEntity<TradeResponse> getReceivedTradeRequests(
      CustomUserDetails customUserDetails,
      TradeRequest tradeRequest
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 858, description = "각 TradeRequestHistory 에 chatRoomId 추가 (채팅방 일괄 조회)"),
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 859, description = "includeRejected 파라미터 추가 (true일 때만 REJECTED 포함), 응답에 추가금·메시지·checkedAt·rejectedAt 추가"),
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
          - **`includeRejected`** (선택, 기본값 `false`): `true`면 거절(REJECTED) 건 포함. 구버전 앱은 미전송 > 기존과 같은 목록
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
          - 보낸 쪽 상태 표시: `PENDING` + `checkedAt == null` 대기, `PENDING` + `checkedAt != null`(또는 `isNew = false`) 확인함, `REJECTED` 거절됨
          - TradeRequestHistory 하나의 객체 내부에 **요청을 받은 물품 (Item takeItem)** 정보, **요청을 보낸 물품 (Item giveItem)** 데이터가 포함됩니다
          """
  )
  ResponseEntity<TradeResponse> getSentTradeRequests(
      CustomUserDetails customUserDetails,
      TradeRequest tradeRequest
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 858, description = "내 보낸 거래 요청 전체 조회 API 추가 (내 물품 수만큼 /get/sent 반복 호출 대체)"),
  })
  @Operation(
      summary = "내 보낸 거래 요청 전체 조회",
      description = """
          ## 인증(JWT): **필요**

          ## 요청 파라미터 (TradeRequest)
          - **`includeRejected`** (선택, 기본값 `false`): `/api/trade/get/sent` 와 같음 (`true`면 거절(REJECTED) 건 포함)
          - **`pageNumber`**: 페이지 번호 (기본값 0)
          - **`pageSize`**: 페이지 크기 (기본값 30)
          - **`sortField`**, **`sortDirection`**: `/api/trade/get/sent` 와 같음 (PRICE = 요청을 받은 물품 가격, AI_RECOMMENDED 는 방향 무시)

          ## 반환값 (TradeResponse)
          - **`tradeRequestHistoryPage`**: 내 모든 물품(giveItem 소유자 = 나)의 보낸 요청 Page (응답 구조는 `/api/trade/get/sent` 와 같음)
            - **`chatRoomId`**: 이 요청의 채팅방 ID (방 없음·삭제 대기 > null)

          ## 설명
          - 조건은 물품별 보낸 요청 조회와 같음: 내 물품 교환 가능(AVAILABLE)·미삭제, 취소(CANCELED) 제외, 거절(REJECTED)은 `includeRejected=true`일 때만 포함, 상호 차단 회원 제외
          - 앱은 페이지 끝(last)까지 이어 조회
          """
  )
  ResponseEntity<TradeResponse> getAllSentTradeRequests(
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
}
