package com.romrom.web.controller.api;

/**
 * 거래 성사율 정렬 Swagger 예시 JSON: 내 물품 5건
 * 물품 소유자 member, 이미지 없는 물품 본문은 공통 조각 재사용
 */
final class TradeApiDocsItemSortExamples {

  private TradeApiDocsItemSortExamples() {
  }

  // 정렬 결과 물품 소유자 member (5건 공통)
  static final String SORTED_ITEM_OWNER_MEMBER_JSON =
      """
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
      """;

  // 이미지 없는 물품의 member 이후 필드 (4건 공통, 닫는 괄호 제외)
  static final String SORTED_PLAIN_ITEM_FIELDS_AFTER_MEMBER_JSON =
      """
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
      """;

  // 거래 성사율 높은 순 내 물품 정렬 응답 예시 (5건)
  static final String SORTED_TRADE_RATE_ITEMS_RESPONSE_JSON =
      """
      {
        "tradeRequestHistory": null,
        "tradeRequestHistoryPage": null,
        "itemPage": {
          "content": [
            {
              "createdDate": "2025-09-18T14:59:59.563502",
              "updatedDate": "2025-09-18T14:59:59.563502",
              "itemId": "bb0841ca-8902-4168-a6fa-d02c0293cfe6",
      """ + SORTED_ITEM_OWNER_MEMBER_JSON + """
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
      """ + SORTED_ITEM_OWNER_MEMBER_JSON + SORTED_PLAIN_ITEM_FIELDS_AFTER_MEMBER_JSON + """
            },
            {
              "createdDate": "2025-09-18T13:59:32.556589",
              "updatedDate": "2025-09-18T13:59:32.556589",
              "itemId": "ddd1eb3e-202e-4b5b-9f0f-a8bae65e503c",
      """ + SORTED_ITEM_OWNER_MEMBER_JSON + SORTED_PLAIN_ITEM_FIELDS_AFTER_MEMBER_JSON + """
            },
            {
              "createdDate": "2025-09-18T14:04:56.225501",
              "updatedDate": "2025-09-18T14:04:56.225501",
              "itemId": "0e01c63c-28a6-4d9c-a832-c433ef90620e",
      """ + SORTED_ITEM_OWNER_MEMBER_JSON + SORTED_PLAIN_ITEM_FIELDS_AFTER_MEMBER_JSON + """
            },
            {
              "createdDate": "2025-09-18T14:07:43.01203",
              "updatedDate": "2025-09-18T14:07:43.01203",
              "itemId": "b2bf9c81-4844-4b71-a67b-8ee7068cf745",
      """ + SORTED_ITEM_OWNER_MEMBER_JSON + SORTED_PLAIN_ITEM_FIELDS_AFTER_MEMBER_JSON + """
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
      """;
}
