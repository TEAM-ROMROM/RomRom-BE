package com.romrom.web.controller.api;

/**
 * 거래 요청 Swagger 예시 JSON: 상세 조회, 받은 요청 목록, 목록 공통 giveItem 조각
 * TradeControllerDocs description 에서 상수 연결로 참조 (렌더링 문자열 동일)
 */
final class TradeApiDocsExamples {

  private TradeApiDocsExamples() {
  }

  // 목록 항목 giveItem(a07fb027) 공통 앞부분 (받은 요청 1건, 보낸 요청 4건)
  static final String LIST_GIVE_ITEM_UNTIL_PRICE_JSON =
      """
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
      """;

  // 목록 항목 giveItem 공통 뒷부분 (좌표, 닫는 괄호)
  static final String LIST_GIVE_ITEM_AFTER_PRICE_JSON =
      """
                "longitude": 0.1,
                "latitude": 0.1
              },
      """;

  // 목록 항목 giveItem: AI 예측가 필드명 isAiPredictedPrice 버전
  static final String LIST_GIVE_ITEM_WITH_IS_AI_PREDICTED_PRICE_JSON =
      LIST_GIVE_ITEM_UNTIL_PRICE_JSON + """
                "isAiPredictedPrice": false,
      """ + LIST_GIVE_ITEM_AFTER_PRICE_JSON;

  // 목록 항목 giveItem: AI 예측가 필드명 aiPredictedPrice 버전
  static final String LIST_GIVE_ITEM_WITH_AI_PREDICTED_PRICE_JSON =
      LIST_GIVE_ITEM_UNTIL_PRICE_JSON + """
                "aiPredictedPrice": false,
      """ + LIST_GIVE_ITEM_AFTER_PRICE_JSON;

  // 거래 요청 상세 조회 응답 예시
  static final String TRADE_REQUEST_HISTORY_DETAIL_RESPONSE_JSON =
      """
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
      """;

  // 받은 거래 요청 목록 응답 예시 (1건)
  static final String RECEIVED_TRADE_REQUESTS_RESPONSE_JSON =
      """
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
      """ + LIST_GIVE_ITEM_WITH_IS_AI_PREDICTED_PRICE_JSON + """
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
      """;
}
