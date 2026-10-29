package com.romrom.web.controller.api;

/**
 * 보낸 거래 요청 목록 Swagger 예시 JSON (4건)
 * giveItem 은 TradeApiDocsExamples 공통 조각 재사용
 */
final class TradeApiDocsSentRequestExamples {

  private TradeApiDocsSentRequestExamples() {
  }

  // 보낸 거래 요청 목록 응답 예시 (4건, giveItem 동일)
  static final String SENT_TRADE_REQUESTS_RESPONSE_JSON =
      """
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
      """ + TradeApiDocsExamples.LIST_GIVE_ITEM_WITH_IS_AI_PREDICTED_PRICE_JSON + """
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
      """ + TradeApiDocsExamples.LIST_GIVE_ITEM_WITH_AI_PREDICTED_PRICE_JSON + """
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
      """ + TradeApiDocsExamples.LIST_GIVE_ITEM_WITH_AI_PREDICTED_PRICE_JSON + """
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
      """ + TradeApiDocsExamples.LIST_GIVE_ITEM_WITH_AI_PREDICTED_PRICE_JSON + """
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
      """;
}
