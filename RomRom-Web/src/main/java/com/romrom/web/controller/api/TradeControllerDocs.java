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
          {
            "tradeRequestHistory": {
              "createdDate": "2025-09-18T15:36:03.26855",
              "updatedDate": "2025-09-18T15:36:03.26855",
              "tradeRequestHistoryId": "28772f8d-5fb8-4b20-8e56-c0a6e9f0c898",
              "takeItem": {
                "createdDate": "2025-09-18T13:26:50.967906",
                "updatedDate": "2025-09-18T13:26:50.967906",
                "itemId": "36f40d25-fd0b-4d95-97aa-91a64a0efa6a",
                "member": {
                  "createdDate": "2025-09-18T13:26:50.945632",
                  "updatedDate": "2025-09-18T13:27:01.872355",
                  "memberId": "a853d068-8166-4791-89c6-4965965a197c",
                  "email": "johnette.smith@yahoo.com",
                  "nickname": "젊은그네-5086",
                  "socialPlatform": "GOOGLE",
                  "profileUrl": "https://picsum.photos/300/400",
                  "role": "ROLE_USER",
                  "accountStatus": "ACTIVE_ACCOUNT",
                  "isFirstLogin": true,
                  "isItemCategorySaved": false,
                  "isFirstItemPosted": true,
                  "isMemberLocationSaved": false,
                  "isRequiredTermsAgreed": false,
                  "isMarketingInfoAgreed": false,
                  "password": null,
                  "latitude": null,
                  "longitude": null
                },
                "itemImages": [
                  {
                    "createdDate": "2025-09-18T13:26:52.634674",
                    "updatedDate": "2025-09-18T13:26:52.634674",
                    "itemImageId": "315bb3cf-024f-409f-8e7b-08e15d75e2ee",
                    "filePath": null,
                    "imageUrl": "https://picsum.photos/300/400?random=608288781259350"
                  },
                  {
                    "createdDate": "2025-09-18T13:26:52.636223",
                    "updatedDate": "2025-09-18T13:26:52.636223",
                    "itemImageId": "7d54ae46-c058-4b9d-bbbb-6af526772253",
                    "filePath": null,
                    "imageUrl": "https://picsum.photos/300/400?random=982497480586255"
                  },
                  {
                    "createdDate": "2025-09-18T13:26:52.636441",
                    "updatedDate": "2025-09-18T13:26:52.636441",
                    "itemImageId": "04613b6d-6c36-4b76-bbce-aa5358a87261",
                    "filePath": null,
                    "imageUrl": "https://picsum.photos/300/400?random=441181897875047"
                  },
                  {
                    "createdDate": "2025-09-18T13:26:52.636636",
                    "updatedDate": "2025-09-18T13:26:52.636636",
                    "itemImageId": "78b9ca06-338b-4359-845f-1228d0e87aa4",
                    "filePath": null,
                    "imageUrl": "https://picsum.photos/300/400?random=857303342393909"
                  }
                ],
                "itemName": "Sleek Bronze Pants",
                "itemDescription": "사생활의 수 아니한다. 체결·공포된 저작자·발명가·과학기술자와 의무교육은 위하여.",
                "itemCategory": "VEHICLES_MOTORCYCLES",
                "itemCondition": "SLIGHTLY_USED",
                "itemStatus": "AVAILABLE",
                "itemTradeOptions": [
                  "EXTRA_CHARGE"
                ],
                "likeCount": 91,
                "price": 62300,
                "isAiPredictedPrice": false,
                "longitude": 128.0934898672305,
                "latitude": 35.05872830297697
              },
              "giveItem": {
                "createdDate": "2025-09-18T13:49:22.925144",
                "updatedDate": "2025-09-18T13:49:22.925144",
                "itemId": "a07fb027-b1ff-406f-8359-e47f21dba125",
                "member": {
                  "createdDate": "2025-09-18T13:41:48.341489",
                  "updatedDate": "2025-09-18T15:35:10.061712",
                  "memberId": "8bd74163-df83-4250-a609-6e288e03b21b",
                  "email": "example@naver.com",
                  "nickname": "울창한케이블-8245",
                  "socialPlatform": "KAKAO",
                  "profileUrl": "https://example.com",
                  "role": "ROLE_USER",
                  "accountStatus": "ACTIVE_ACCOUNT",
                  "isFirstLogin": false,
                  "isItemCategorySaved": false,
                  "isFirstItemPosted": true,
                  "isMemberLocationSaved": true,
                  "isRequiredTermsAgreed": false,
                  "isMarketingInfoAgreed": false,
                  "password": null,
                  "latitude": null,
                  "longitude": null
                },
                "itemImages": [],
                "itemName": "string",
                "itemDescription": "string",
                "itemCategory": "WOMEN_CLOTHING",
                "itemCondition": "SEALED",
                "itemStatus": "AVAILABLE",
                "itemTradeOptions": [
                  "EXTRA_CHARGE"
                ],
                "likeCount": 0,
                "price": 1073741824,
                "isAiPredictedPrice": false,
                "longitude": 0.1,
                "latitude": 0.1
              },
              "itemTradeOptions": [
                "EXTRA_CHARGE"
              ],
              "tradeStatus": "PENDING",
              "isNew": true
            },
            "tradeRequestHistoryPage": null,
            "itemPage": null
          }
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
          
          ## 반환값 (TradeResponse)
          - **`Page<TradeRequestHistory>`**: TradeRequestHistory 객체 Page
          
          ## 반환값 예시
          ```
          {
            "tradeRequestHistory": null,
            "tradeRequestHistoryPage": {
              "content": [
                {
                  "createdDate": "2025-09-18T15:47:53.360287",
                  "updatedDate": "2025-09-18T15:47:53.360287",
                  "tradeRequestHistoryId": "78783c69-09c0-45d9-9247-e2d496f9802f",
                  "takeItem": {
                    "createdDate": "2025-09-18T13:40:36.478223",
                    "updatedDate": "2025-09-18T15:19:47.398513",
                    "itemId": "e7ce4f1a-9935-4f4e-8a01-508b2832cfa0",
                    "member": {
                      "createdDate": "2025-09-18T11:02:03.125039",
                      "updatedDate": "2025-09-18T13:40:38.236136",
                      "memberId": "2d978675-0e37-4a6c-91f3-9866df0a3411",
                      "email": "bjh59629@naver.com",
                      "nickname": "한들한들강-1124",
                      "socialPlatform": "KAKAO",
                      "profileUrl": "https://example.com",
                      "role": "ROLE_USER",
                      "accountStatus": "ACTIVE_ACCOUNT",
                      "isFirstLogin": false,
                      "isItemCategorySaved": true,
                      "isFirstItemPosted": true,
                      "isMemberLocationSaved": true,
                      "isRequiredTermsAgreed": true,
                      "isMarketingInfoAgreed": true,
                      "password": null,
                      "latitude": null,
                      "longitude": null
                    },
                    "itemImages": [],
                    "itemName": "string",
                    "itemDescription": "string",
                    "itemCategory": "WOMEN_CLOTHING",
                    "itemCondition": "SEALED",
                    "itemStatus": "AVAILABLE",
                    "itemTradeOptions": [
                      "EXTRA_CHARGE"
                    ],
                    "likeCount": 1,
                    "price": 1073741824,
                    "isAiPredictedPrice": false,
                    "longitude": 0.1,
                    "latitude": 0.1
                  },
                  "giveItem": {
                    "createdDate": "2025-09-18T13:49:22.925144",
                    "updatedDate": "2025-09-18T13:49:22.925144",
                    "itemId": "a07fb027-b1ff-406f-8359-e47f21dba125",
                    "member": {
                      "createdDate": "2025-09-18T13:41:48.341489",
                      "updatedDate": "2025-09-18T15:35:10.061712",
                      "memberId": "8bd74163-df83-4250-a609-6e288e03b21b",
                      "email": "example@naver.com",
                      "nickname": "울창한케이블-8245",
                      "socialPlatform": "KAKAO",
                      "profileUrl": "https://example.com",
                      "role": "ROLE_USER",
                      "accountStatus": "ACTIVE_ACCOUNT",
                      "isFirstLogin": false,
                      "isItemCategorySaved": false,
                      "isFirstItemPosted": true,
                      "isMemberLocationSaved": true,
                      "isRequiredTermsAgreed": false,
                      "isMarketingInfoAgreed": false,
                      "password": null,
                      "latitude": null,
                      "longitude": null
                    },
                    "itemImages": [],
                    "itemName": "string",
                    "itemDescription": "string",
                    "itemCategory": "WOMEN_CLOTHING",
                    "itemCondition": "SEALED",
                    "itemStatus": "AVAILABLE",
                    "itemTradeOptions": [
                      "EXTRA_CHARGE"
                    ],
                    "likeCount": 0,
                    "price": 1073741824,
                    "isAiPredictedPrice": false,
                    "longitude": 0.1,
                    "latitude": 0.1
                  },
                  "itemTradeOptions": [
                    "EXTRA_CHARGE"
                  ],
                  "tradeStatus": "PENDING"
                }
              ],
              "page": {
                "size": 30,
                "number": 0,
                "totalElements": 1,
                "totalPages": 1
              }
            },
            "itemPage": null
          }
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
          
          ## 반환값 (TradeResponse)
          - **`Page<TradeRequestHistory>`**: TradeRequestHistory 객체 Page
          
          ## 반환값 예시
          ```
          {
            "tradeRequestHistory": null,
            "tradeRequestHistoryPage": {
              "content": [
                {
                  "createdDate": "2025-09-18T15:36:32.142715",
                  "updatedDate": "2025-09-18T15:36:32.142715",
                  "tradeRequestHistoryId": "8e52f514-5c5f-41a6-9702-478382bf7287",
                  "takeItem": {
                    "createdDate": "2025-09-18T13:26:53.681673",
                    "updatedDate": "2025-09-18T13:26:53.681673",
                    "itemId": "0798d39d-9625-43d6-a3c5-51e16ebce74b",
                    "member": {
                      "createdDate": "2025-09-18T13:26:53.681012",
                      "updatedDate": "2025-09-18T13:27:01.878923",
                      "memberId": "5e6958c1-7f57-4d01-8861-298b7641dcb9",
                      "email": "jana.davis@gmail.com",
                      "nickname": "못난접시-2014",
                      "socialPlatform": "NORMAL",
                      "profileUrl": "https://picsum.photos/300/400",
                      "role": "ROLE_USER",
                      "accountStatus": "ACTIVE_ACCOUNT",
                      "isFirstLogin": true,
                      "isItemCategorySaved": false,
                      "isFirstItemPosted": true,
                      "isMemberLocationSaved": false,
                      "isRequiredTermsAgreed": false,
                      "isMarketingInfoAgreed": false,
                      "password": null,
                      "latitude": null,
                      "longitude": null
                    },
                    "itemImages": [
                      {
                        "createdDate": "2025-09-18T13:26:54.291434",
                        "updatedDate": "2025-09-18T13:26:54.291434",
                        "itemImageId": "80dd70a0-09e2-460e-9bb8-e9fccad50114",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=685252680832936"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:54.291983",
                        "updatedDate": "2025-09-18T13:26:54.291983",
                        "itemImageId": "f18352d4-debb-4de0-bad7-2360b3a47e08",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=969856892486069"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:54.292347",
                        "updatedDate": "2025-09-18T13:26:54.292347",
                        "itemImageId": "0c6a90e8-d064-4cb5-8087-ff42a0a2ed02",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=862799008533296"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:54.292629",
                        "updatedDate": "2025-09-18T13:26:54.292629",
                        "itemImageId": "1dade6ff-db72-426e-9403-853731e758d9",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=266019662946929"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:54.292938",
                        "updatedDate": "2025-09-18T13:26:54.292938",
                        "itemImageId": "e38716a9-1196-4079-b3f4-9e8368feebe0",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=171795479127512"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:54.293223",
                        "updatedDate": "2025-09-18T13:26:54.293223",
                        "itemImageId": "f97f4a3d-793e-4730-91b5-6549445d7fd0",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=597807735119701"
                      }
                    ],
                    "itemName": "Lightweight Copper Table",
                    "itemDescription": "국민은 의하여 염려가 자유를.",
                    "itemCategory": "LIFE_KITCHEN",
                    "itemCondition": "SEALED",
                    "itemStatus": "AVAILABLE",
                    "itemTradeOptions": [
                      "DELIVERY_ONLY",
                      "EXTRA_CHARGE"
                    ],
                    "likeCount": 76,
                    "price": 78800,
                    "isAiPredictedPrice": false,
                    "longitude": 130.82106461767538,
                    "latitude": 36.619104066040926
                  },
                  "giveItem": {
                    "createdDate": "2025-09-18T13:49:22.925144",
                    "updatedDate": "2025-09-18T13:49:22.925144",
                    "itemId": "a07fb027-b1ff-406f-8359-e47f21dba125",
                    "member": {
                      "createdDate": "2025-09-18T13:41:48.341489",
                      "updatedDate": "2025-09-18T15:35:10.061712",
                      "memberId": "8bd74163-df83-4250-a609-6e288e03b21b",
                      "email": "example@naver.com",
                      "nickname": "울창한케이블-8245",
                      "socialPlatform": "KAKAO",
                      "profileUrl": "https://example.com",
                      "role": "ROLE_USER",
                      "accountStatus": "ACTIVE_ACCOUNT",
                      "isFirstLogin": false,
                      "isItemCategorySaved": false,
                      "isFirstItemPosted": true,
                      "isMemberLocationSaved": true,
                      "isRequiredTermsAgreed": false,
                      "isMarketingInfoAgreed": false,
                      "password": null,
                      "latitude": null,
                      "longitude": null
                    },
                    "itemImages": [],
                    "itemName": "string",
                    "itemDescription": "string",
                    "itemCategory": "WOMEN_CLOTHING",
                    "itemCondition": "SEALED",
                    "itemStatus": "AVAILABLE",
                    "itemTradeOptions": [
                      "EXTRA_CHARGE"
                    ],
                    "likeCount": 0,
                    "price": 1073741824,
                    "isAiPredictedPrice": false,
                    "longitude": 0.1,
                    "latitude": 0.1
                  },
                  "itemTradeOptions": [
                    "EXTRA_CHARGE"
                  ],
                  "tradeStatus": "PENDING"
                },
                {
                  "createdDate": "2025-09-18T15:36:21.460838",
                  "updatedDate": "2025-09-18T15:36:21.460838",
                  "tradeRequestHistoryId": "f2ebe835-220e-4469-895f-20e60e4c452c",
                  "takeItem": {
                    "createdDate": "2025-09-18T13:26:53.069859",
                    "updatedDate": "2025-09-18T13:26:53.069859",
                    "itemId": "ab7bcfa5-2b24-4348-8e6a-7100a3031862",
                    "member": {
                      "createdDate": "2025-09-18T13:26:53.069001",
                      "updatedDate": "2025-09-18T13:27:01.87848",
                      "memberId": "c8f9b866-be51-4e00-a675-a44889e90e30",
                      "email": "lesley.hilpert@yahoo.com",
                      "nickname": "짖궂은바람개비-0665",
                      "socialPlatform": "GOOGLE",
                      "profileUrl": "https://picsum.photos/300/400",
                      "role": "ROLE_USER",
                      "accountStatus": "ACTIVE_ACCOUNT",
                      "isFirstLogin": true,
                      "isItemCategorySaved": false,
                      "isFirstItemPosted": true,
                      "isMemberLocationSaved": false,
                      "isRequiredTermsAgreed": false,
                      "isMarketingInfoAgreed": false,
                      "password": null,
                      "latitude": null,
                      "longitude": null
                    },
                    "itemImages": [
                      {
                        "createdDate": "2025-09-18T13:26:53.676694",
                        "updatedDate": "2025-09-18T13:26:53.676694",
                        "itemImageId": "6350254d-3991-442a-847d-b96e06fb63cb",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=684227667226505"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.67738",
                        "updatedDate": "2025-09-18T13:26:53.67738",
                        "itemImageId": "501af5e2-6de0-4ce9-b33f-bc37b1cde7de",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=993063009517132"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.677849",
                        "updatedDate": "2025-09-18T13:26:53.677849",
                        "itemImageId": "9d96bb6e-935c-4c95-a8fd-35ed7d2002a1",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=343000645523929"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.678283",
                        "updatedDate": "2025-09-18T13:26:53.678283",
                        "itemImageId": "643a0f47-cc5e-4110-a658-1c48aa2c387e",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=406520942692716"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.678572",
                        "updatedDate": "2025-09-18T13:26:53.678572",
                        "itemImageId": "6f5484ca-89dc-43ab-a410-572b8a609a85",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=183263764006506"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.67885",
                        "updatedDate": "2025-09-18T13:26:53.67885",
                        "itemImageId": "7d60722b-a51e-4a3b-a94e-7ca2085b089c",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=624520802641970"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.679175",
                        "updatedDate": "2025-09-18T13:26:53.679175",
                        "itemImageId": "bc7d9227-3a70-4036-a36f-b591f9c13271",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=946460957831283"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.679458",
                        "updatedDate": "2025-09-18T13:26:53.679458",
                        "itemImageId": "d5da36d3-9067-4d04-a1b2-34857f9611ef",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=713842636129210"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.679731",
                        "updatedDate": "2025-09-18T13:26:53.679731",
                        "itemImageId": "886ac2aa-a9aa-49c9-bda8-84bf1cb319a1",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=836311007010125"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.679992",
                        "updatedDate": "2025-09-18T13:26:53.679992",
                        "itemImageId": "63d3e811-c53f-489b-913a-3423ceb157ae",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=387909752617308"
                      }
                    ],
                    "itemName": "Enormous Marble Gloves",
                    "itemDescription": "영장을 의하여 모든 체결·공포된.",
                    "itemCategory": "MEN_CLOTHING",
                    "itemCondition": "HEAVILY_USED",
                    "itemStatus": "AVAILABLE",
                    "itemTradeOptions": [
                      "DELIVERY_ONLY",
                      "EXTRA_CHARGE"
                    ],
                    "likeCount": 48,
                    "price": 66100,
                    "aiPredictedPrice": false,
                    "longitude": 127.5981570233142,
                    "latitude": 36.073613625602164
                  },
                  "giveItem": {
                    "createdDate": "2025-09-18T13:49:22.925144",
                    "updatedDate": "2025-09-18T13:49:22.925144",
                    "itemId": "a07fb027-b1ff-406f-8359-e47f21dba125",
                    "member": {
                      "createdDate": "2025-09-18T13:41:48.341489",
                      "updatedDate": "2025-09-18T15:35:10.061712",
                      "memberId": "8bd74163-df83-4250-a609-6e288e03b21b",
                      "email": "example@naver.com",
                      "nickname": "울창한케이블-8245",
                      "socialPlatform": "KAKAO",
                      "profileUrl": "https://example.com",
                      "role": "ROLE_USER",
                      "accountStatus": "ACTIVE_ACCOUNT",
                      "isFirstLogin": false,
                      "isItemCategorySaved": false,
                      "isFirstItemPosted": true,
                      "isMemberLocationSaved": true,
                      "isRequiredTermsAgreed": false,
                      "isMarketingInfoAgreed": false,
                      "password": null,
                      "latitude": null,
                      "longitude": null
                    },
                    "itemImages": [],
                    "itemName": "string",
                    "itemDescription": "string",
                    "itemCategory": "WOMEN_CLOTHING",
                    "itemCondition": "SEALED",
                    "itemStatus": "AVAILABLE",
                    "itemTradeOptions": [
                      "EXTRA_CHARGE"
                    ],
                    "likeCount": 0,
                    "price": 1073741824,
                    "aiPredictedPrice": false,
                    "longitude": 0.1,
                    "latitude": 0.1
                  },
                  "itemTradeOptions": [
                    "EXTRA_CHARGE"
                  ],
                  "tradeStatus": "PENDING"
                },
                {
                  "createdDate": "2025-09-18T15:36:16.335085",
                  "updatedDate": "2025-09-18T15:36:16.335085",
                  "tradeRequestHistoryId": "d06ad7ef-7d9f-401d-92a1-28774f09faa9",
                  "takeItem": {
                    "createdDate": "2025-09-18T13:26:52.63794",
                    "updatedDate": "2025-09-18T13:26:52.63794",
                    "itemId": "7e412e8d-a677-4c83-a53e-8fe181116368",
                    "member": {
                      "createdDate": "2025-09-18T13:26:52.637449",
                      "updatedDate": "2025-09-18T13:27:01.877926",
                      "memberId": "0c465487-ff6c-4ef1-b112-97cb555d365d",
                      "email": "fawn.heathcote@yahoo.com",
                      "nickname": "기쁜학교-7451",
                      "socialPlatform": "NORMAL",
                      "profileUrl": "https://picsum.photos/300/400",
                      "role": "ROLE_USER",
                      "accountStatus": "ACTIVE_ACCOUNT",
                      "isFirstLogin": true,
                      "isItemCategorySaved": false,
                      "isFirstItemPosted": true,
                      "isMemberLocationSaved": false,
                      "isRequiredTermsAgreed": false,
                      "isMarketingInfoAgreed": false,
                      "password": null,
                      "latitude": null,
                      "longitude": null
                    },
                    "itemImages": [
                      {
                        "createdDate": "2025-09-18T13:26:53.064366",
                        "updatedDate": "2025-09-18T13:26:53.064366",
                        "itemImageId": "45d713b3-1c1c-4bc0-95a6-39d555cae127",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=861354169717502"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.065034",
                        "updatedDate": "2025-09-18T13:26:53.065034",
                        "itemImageId": "1dd8614e-71cb-4133-8351-c571cbfece33",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=156705535566365"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.065384",
                        "updatedDate": "2025-09-18T13:26:53.065384",
                        "itemImageId": "d31d5f8d-b8f8-4bfc-910d-e566fd52418c",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=378087534760371"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.065704",
                        "updatedDate": "2025-09-18T13:26:53.065704",
                        "itemImageId": "dd333cd3-54fe-4797-b207-455d1ae36ede",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=457507905581797"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.06677",
                        "updatedDate": "2025-09-18T13:26:53.06677",
                        "itemImageId": "2d50b52c-d6de-4249-83ac-27b726ff51cd",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=373699040828742"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.06731",
                        "updatedDate": "2025-09-18T13:26:53.06731",
                        "itemImageId": "e9db0c56-efa9-48a7-9d4e-c67eececc1eb",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=480747464095166"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:53.067633",
                        "updatedDate": "2025-09-18T13:26:53.067633",
                        "itemImageId": "ad6f565c-1c5c-4d6c-b564-602e651bbc7a",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=257612622779521"
                      }
                    ],
                    "itemName": "Aerodynamic Wooden Keyboard",
                    "itemDescription": "죄를 저작자·발명가·과학기술자와 보호한다..",
                    "itemCategory": "BOOKS_TICKETS_STATIONERY",
                    "itemCondition": "SEALED",
                    "itemStatus": "AVAILABLE",
                    "itemTradeOptions": [
                      "EXTRA_CHARGE",
                      "DELIVERY_ONLY"
                    ],
                    "likeCount": 48,
                    "price": 53800,
                    "aiPredictedPrice": false,
                    "longitude": 125.86524143885734,
                    "latitude": 33.33203387166089
                  },
                  "giveItem": {
                    "createdDate": "2025-09-18T13:49:22.925144",
                    "updatedDate": "2025-09-18T13:49:22.925144",
                    "itemId": "a07fb027-b1ff-406f-8359-e47f21dba125",
                    "member": {
                      "createdDate": "2025-09-18T13:41:48.341489",
                      "updatedDate": "2025-09-18T15:35:10.061712",
                      "memberId": "8bd74163-df83-4250-a609-6e288e03b21b",
                      "email": "example@naver.com",
                      "nickname": "울창한케이블-8245",
                      "socialPlatform": "KAKAO",
                      "profileUrl": "https://example.com",
                      "role": "ROLE_USER",
                      "accountStatus": "ACTIVE_ACCOUNT",
                      "isFirstLogin": false,
                      "isItemCategorySaved": false,
                      "isFirstItemPosted": true,
                      "isMemberLocationSaved": true,
                      "isRequiredTermsAgreed": false,
                      "isMarketingInfoAgreed": false,
                      "password": null,
                      "latitude": null,
                      "longitude": null
                    },
                    "itemImages": [],
                    "itemName": "string",
                    "itemDescription": "string",
                    "itemCategory": "WOMEN_CLOTHING",
                    "itemCondition": "SEALED",
                    "itemStatus": "AVAILABLE",
                    "itemTradeOptions": [
                      "EXTRA_CHARGE"
                    ],
                    "likeCount": 0,
                    "price": 1073741824,
                    "aiPredictedPrice": false,
                    "longitude": 0.1,
                    "latitude": 0.1
                  },
                  "itemTradeOptions": [
                    "EXTRA_CHARGE"
                  ],
                  "tradeStatus": "PENDING"
                },
                {
                  "createdDate": "2025-09-18T15:36:03.26855",
                  "updatedDate": "2025-09-18T15:36:03.26855",
                  "tradeRequestHistoryId": "28772f8d-5fb8-4b20-8e56-c0a6e9f0c898",
                  "takeItem": {
                    "createdDate": "2025-09-18T13:26:50.967906",
                    "updatedDate": "2025-09-18T13:26:50.967906",
                    "itemId": "36f40d25-fd0b-4d95-97aa-91a64a0efa6a",
                    "member": {
                      "createdDate": "2025-09-18T13:26:50.945632",
                      "updatedDate": "2025-09-18T13:27:01.872355",
                      "memberId": "a853d068-8166-4791-89c6-4965965a197c",
                      "email": "johnette.smith@yahoo.com",
                      "nickname": "젊은그네-5086",
                      "socialPlatform": "GOOGLE",
                      "profileUrl": "https://picsum.photos/300/400",
                      "role": "ROLE_USER",
                      "accountStatus": "ACTIVE_ACCOUNT",
                      "isFirstLogin": true,
                      "isItemCategorySaved": false,
                      "isFirstItemPosted": true,
                      "isMemberLocationSaved": false,
                      "isRequiredTermsAgreed": false,
                      "isMarketingInfoAgreed": false,
                      "password": null,
                      "latitude": null,
                      "longitude": null
                    },
                    "itemImages": [
                      {
                        "createdDate": "2025-09-18T13:26:52.634674",
                        "updatedDate": "2025-09-18T13:26:52.634674",
                        "itemImageId": "315bb3cf-024f-409f-8e7b-08e15d75e2ee",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=608288781259350"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:52.636223",
                        "updatedDate": "2025-09-18T13:26:52.636223",
                        "itemImageId": "7d54ae46-c058-4b9d-bbbb-6af526772253",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=982497480586255"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:52.636441",
                        "updatedDate": "2025-09-18T13:26:52.636441",
                        "itemImageId": "04613b6d-6c36-4b76-bbce-aa5358a87261",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=441181897875047"
                      },
                      {
                        "createdDate": "2025-09-18T13:26:52.636636",
                        "updatedDate": "2025-09-18T13:26:52.636636",
                        "itemImageId": "78b9ca06-338b-4359-845f-1228d0e87aa4",
                        "filePath": null,
                        "imageUrl": "https://picsum.photos/300/400?random=857303342393909"
                      }
                    ],
                    "itemName": "Sleek Bronze Pants",
                    "itemDescription": "사생활의 수 아니한다. 체결·공포된 저작자·발명가·과학기술자와 의무교육은 위하여.",
                    "itemCategory": "VEHICLES_MOTORCYCLES",
                    "itemCondition": "SLIGHTLY_USED",
                    "itemStatus": "AVAILABLE",
                    "itemTradeOptions": [
                      "EXTRA_CHARGE"
                    ],
                    "likeCount": 91,
                    "price": 62300,
                    "aiPredictedPrice": false,
                    "longitude": 128.0934898672305,
                    "latitude": 35.05872830297697
                  },
                  "giveItem": {
                    "createdDate": "2025-09-18T13:49:22.925144",
                    "updatedDate": "2025-09-18T13:49:22.925144",
                    "itemId": "a07fb027-b1ff-406f-8359-e47f21dba125",
                    "member": {
                      "createdDate": "2025-09-18T13:41:48.341489",
                      "updatedDate": "2025-09-18T15:35:10.061712",
                      "memberId": "8bd74163-df83-4250-a609-6e288e03b21b",
                      "email": "example@naver.com",
                      "nickname": "울창한케이블-8245",
                      "socialPlatform": "KAKAO",
                      "profileUrl": "https://example.com",
                      "role": "ROLE_USER",
                      "accountStatus": "ACTIVE_ACCOUNT",
                      "isFirstLogin": false,
                      "isItemCategorySaved": false,
                      "isFirstItemPosted": true,
                      "isMemberLocationSaved": true,
                      "isRequiredTermsAgreed": false,
                      "isMarketingInfoAgreed": false,
                      "password": null,
                      "latitude": null,
                      "longitude": null
                    },
                    "itemImages": [],
                    "itemName": "string",
                    "itemDescription": "string",
                    "itemCategory": "WOMEN_CLOTHING",
                    "itemCondition": "SEALED",
                    "itemStatus": "AVAILABLE",
                    "itemTradeOptions": [
                      "EXTRA_CHARGE"
                    ],
                    "likeCount": 0,
                    "price": 1073741824,
                    "aiPredictedPrice": false,
                    "longitude": 0.1,
                    "latitude": 0.1
                  },
                  "itemTradeOptions": [
                    "EXTRA_CHARGE"
                  ],
                  "tradeStatus": "PENDING"
                }
              ],
              "page": {
                "size": 30,
                "number": 0,
                "totalElements": 4,
                "totalPages": 1
              }
            },
            "itemPage": null
          }
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
          {
            "tradeRequestHistory": null,
            "tradeRequestHistoryPage": null,
            "itemPage": {
              "content": [
                {
                  "createdDate": "2025-09-18T14:59:59.563502",
                  "updatedDate": "2025-09-18T14:59:59.563502",
                  "itemId": "bb0841ca-8902-4168-a6fa-d02c0293cfe6",
                  "member": {
                    "createdDate": "2025-09-18T13:41:48.341489",
                    "updatedDate": "2025-09-18T15:35:10.061712",
                    "memberId": "8bd74163-df83-4250-a609-6e288e03b21b",
                    "email": "example@naver.com",
                    "nickname": "울창한케이블-8245",
                    "socialPlatform": "KAKAO",
                    "profileUrl": "https://example.com",
                    "role": "ROLE_USER",
                    "accountStatus": "ACTIVE_ACCOUNT",
                    "isFirstLogin": false,
                    "isItemCategorySaved": false,
                    "isFirstItemPosted": true,
                    "isMemberLocationSaved": true,
                    "isRequiredTermsAgreed": false,
                    "isMarketingInfoAgreed": false,
                    "password": null,
                    "latitude": null,
                    "longitude": null
                  },
                  "itemImages": [
                    {
                      "createdDate": "2025-09-18T15:00:01.166167",
                      "updatedDate": "2025-09-18T15:00:01.166167",
                      "itemImageId": "5fae2faf-2705-4c0d-9558-8f3c834445be",
                      "filePath": "string",
                      "imageUrl": "http://suh-project.synology.me/string"
                    },
                    {
                      "createdDate": "2025-09-18T15:00:01.167707",
                      "updatedDate": "2025-09-18T15:00:01.167707",
                      "itemImageId": "df0bd681-ac47-471f-a259-7aad164fcd8e",
                      "filePath": "string111",
                      "imageUrl": "http://suh-project.synology.me/string111"
                    },
                    {
                      "createdDate": "2025-09-18T15:00:01.167838",
                      "updatedDate": "2025-09-18T15:00:01.167838",
                      "itemImageId": "75c2a3aa-3603-4d8f-952f-0fdf16a50525",
                      "filePath": "string222",
                      "imageUrl": "http://suh-project.synology.me/string222"
                    }
                  ],
                  "itemName": "물품예시",
                  "itemDescription": "설명",
                  "itemCategory": "WOMEN_CLOTHING",
                  "itemCondition": "SEALED",
                  "itemStatus": "AVAILABLE",
                  "itemTradeOptions": [
                    "EXTRA_CHARGE"
                  ],
                  "likeCount": 0,
                  "price": 12000,
                  "aiPredictedPrice": false,
                  "longitude": 0.1,
                  "latitude": 0.1
                },
                {
                  "createdDate": "2025-09-18T13:49:22.925144",
                  "updatedDate": "2025-09-18T13:49:22.925144",
                  "itemId": "a07fb027-b1ff-406f-8359-e47f21dba125",
                  "member": {
                    "createdDate": "2025-09-18T13:41:48.341489",
                    "updatedDate": "2025-09-18T15:35:10.061712",
                    "memberId": "8bd74163-df83-4250-a609-6e288e03b21b",
                    "email": "example@naver.com",
                    "nickname": "울창한케이블-8245",
                    "socialPlatform": "KAKAO",
                    "profileUrl": "https://example.com",
                    "role": "ROLE_USER",
                    "accountStatus": "ACTIVE_ACCOUNT",
                    "isFirstLogin": false,
                    "isItemCategorySaved": false,
                    "isFirstItemPosted": true,
                    "isMemberLocationSaved": true,
                    "isRequiredTermsAgreed": false,
                    "isMarketingInfoAgreed": false,
                    "password": null,
                    "latitude": null,
                    "longitude": null
                  },
                  "itemImages": [],
                  "itemName": "string",
                  "itemDescription": "string",
                  "itemCategory": "WOMEN_CLOTHING",
                  "itemCondition": "SEALED",
                  "itemStatus": "AVAILABLE",
                  "itemTradeOptions": [
                    "EXTRA_CHARGE"
                  ],
                  "likeCount": 0,
                  "price": 1073741824,
                  "aiPredictedPrice": false,
                  "longitude": 0.1,
                  "latitude": 0.1
                },
                {
                  "createdDate": "2025-09-18T13:59:32.556589",
                  "updatedDate": "2025-09-18T13:59:32.556589",
                  "itemId": "ddd1eb3e-202e-4b5b-9f0f-a8bae65e503c",
                  "member": {
                    "createdDate": "2025-09-18T13:41:48.341489",
                    "updatedDate": "2025-09-18T15:35:10.061712",
                    "memberId": "8bd74163-df83-4250-a609-6e288e03b21b",
                    "email": "example@naver.com",
                    "nickname": "울창한케이블-8245",
                    "socialPlatform": "KAKAO",
                    "profileUrl": "https://example.com",
                    "role": "ROLE_USER",
                    "accountStatus": "ACTIVE_ACCOUNT",
                    "isFirstLogin": false,
                    "isItemCategorySaved": false,
                    "isFirstItemPosted": true,
                    "isMemberLocationSaved": true,
                    "isRequiredTermsAgreed": false,
                    "isMarketingInfoAgreed": false,
                    "password": null,
                    "latitude": null,
                    "longitude": null
                  },
                  "itemImages": [],
                  "itemName": "string",
                  "itemDescription": "string",
                  "itemCategory": "WOMEN_CLOTHING",
                  "itemCondition": "SEALED",
                  "itemStatus": "AVAILABLE",
                  "itemTradeOptions": [
                    "EXTRA_CHARGE"
                  ],
                  "likeCount": 0,
                  "price": 1073741824,
                  "aiPredictedPrice": false,
                  "longitude": 0.1,
                  "latitude": 0.1
                },
                {
                  "createdDate": "2025-09-18T14:04:56.225501",
                  "updatedDate": "2025-09-18T14:04:56.225501",
                  "itemId": "0e01c63c-28a6-4d9c-a832-c433ef90620e",
                  "member": {
                    "createdDate": "2025-09-18T13:41:48.341489",
                    "updatedDate": "2025-09-18T15:35:10.061712",
                    "memberId": "8bd74163-df83-4250-a609-6e288e03b21b",
                    "email": "example@naver.com",
                    "nickname": "울창한케이블-8245",
                    "socialPlatform": "KAKAO",
                    "profileUrl": "https://example.com",
                    "role": "ROLE_USER",
                    "accountStatus": "ACTIVE_ACCOUNT",
                    "isFirstLogin": false,
                    "isItemCategorySaved": false,
                    "isFirstItemPosted": true,
                    "isMemberLocationSaved": true,
                    "isRequiredTermsAgreed": false,
                    "isMarketingInfoAgreed": false,
                    "password": null,
                    "latitude": null,
                    "longitude": null
                  },
                  "itemImages": [],
                  "itemName": "string",
                  "itemDescription": "string",
                  "itemCategory": "WOMEN_CLOTHING",
                  "itemCondition": "SEALED",
                  "itemStatus": "AVAILABLE",
                  "itemTradeOptions": [
                    "EXTRA_CHARGE"
                  ],
                  "likeCount": 0,
                  "price": 1073741824,
                  "aiPredictedPrice": false,
                  "longitude": 0.1,
                  "latitude": 0.1
                },
                {
                  "createdDate": "2025-09-18T14:07:43.01203",
                  "updatedDate": "2025-09-18T14:07:43.01203",
                  "itemId": "b2bf9c81-4844-4b71-a67b-8ee7068cf745",
                  "member": {
                    "createdDate": "2025-09-18T13:41:48.341489",
                    "updatedDate": "2025-09-18T15:35:10.061712",
                    "memberId": "8bd74163-df83-4250-a609-6e288e03b21b",
                    "email": "example@naver.com",
                    "nickname": "울창한케이블-8245",
                    "socialPlatform": "KAKAO",
                    "profileUrl": "https://example.com",
                    "role": "ROLE_USER",
                    "accountStatus": "ACTIVE_ACCOUNT",
                    "isFirstLogin": false,
                    "isItemCategorySaved": false,
                    "isFirstItemPosted": true,
                    "isMemberLocationSaved": true,
                    "isRequiredTermsAgreed": false,
                    "isMarketingInfoAgreed": false,
                    "password": null,
                    "latitude": null,
                    "longitude": null
                  },
                  "itemImages": [],
                  "itemName": "string",
                  "itemDescription": "string",
                  "itemCategory": "WOMEN_CLOTHING",
                  "itemCondition": "SEALED",
                  "itemStatus": "AVAILABLE",
                  "itemTradeOptions": [
                    "EXTRA_CHARGE"
                  ],
                  "likeCount": 0,
                  "price": 1073741824,
                  "aiPredictedPrice": false,
                  "longitude": 0.1,
                  "latitude": 0.1
                }
              ],
              "page": {
                "size": 30,
                "number": 0,
                "totalElements": 5,
                "totalPages": 1
              }
            }
          }
          ```
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          - **`EMBEDDING_NOT_FOUND`**: 임베딩을 찾을 수 없습니다.
          """
  )
  ResponseEntity<TradeResponse> getSortedTradeRate(CustomUserDetails customUserDetails, TradeRequest tradeRequest);

  @ApiChangeLogs({
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
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
          """
  )
  ResponseEntity<TradeResponse> getAiRecommendedItems(CustomUserDetails customUserDetails, TradeRequest tradeRequest);
}
