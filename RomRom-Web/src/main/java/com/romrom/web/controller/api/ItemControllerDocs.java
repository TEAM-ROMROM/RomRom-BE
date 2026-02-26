package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface ItemControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.10.02", author = Author.SUHSAECHAN, issueNumber = 366, description = "물품 등록 api 반환값 추가 isFirstItemPosted 칼럼 추가"),
      @ApiChangeLog(date = "2025.10.02", author = Author.SUHSAECHAN, issueNumber = 366, description = "물품 등록 api 반환값 추가 item추가"),
      @ApiChangeLog(date = "2025.09.18", author = Author.BAEKJIHOON, issueNumber = 336, description = "물품 등록 api 반환값 제거"),
      @ApiChangeLog(date = "2025.08.08", author = Author.BAEKJIHOON, issueNumber = 262, description = "물품 등록 시 AI 가격 측정 여부 저장 및 반환"),
      @ApiChangeLog(date = "2025.08.03", author = Author.KIMNAYOUNG, issueNumber = 243, description = "이미지 업로드 워크플로우 개선"),
      @ApiChangeLog(date = "2025.07.25", author = Author.KIMNAYOUNG, issueNumber = 234, description = "거래 희망 위치 추가"),
      @ApiChangeLog(date = "2025.03.22", author = Author.WISEUNGJAE, issueNumber = 60, description = "물품 등록 로직에 커스텀 태그 등록 로직 추가"),
      @ApiChangeLog(date = "2025.03.15", author = Author.KIMNAYOUNG, issueNumber = 55, description = "물품 등록 로직 생성"),
  })
  @Operation(
      summary = "물품 등록",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (ItemRequest)
          - **`member`**: 회원
          - **`itemImageUrls`**: 물품 사진 URL
          - **`itemName`**: 물품명
          - **`itemDescription`**: 물품 상세 설명
          - **`itemCategory`**: 물품 카테고리
          - **`itemCondition`**: 물품 상태
          - **`itemTradeOptions`**: 물품 옵션
          - **`itemPrice`**: 가격
          - **`itemCustomTags`**: 커스텀 태그
          - **`longitude`**: 거래 희망 위치 경도
          - **`latitude`**: 거래 희망 위치 위도
          - **`isAiPredictedPrice`**: AI 가격측정 여부
          
          ## 반환값 (ItemResponse)
          - **`item`**: 생성된 물품 정보
          - **`isFirstItemPosted`**: 사용자의 첫 물품 등록 여부 (boolean)
          """
  )
  ResponseEntity<ItemResponse> postItem(
      CustomUserDetails customUserDetails,
      ItemRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "차단된 회원의 물품에 좋아요 등록, 취소 방지 로직 추가"),
      @ApiChangeLog(date = "2025.09.18", author = Author.BAEKJIHOON, issueNumber = 336, description = "물품 좋아요/취소 반환값 구조 개선"),
      @ApiChangeLog(date = "2025.06.30", author = Author.SUHSAECHAN, issueNumber = 72, description = "반환값 요청값 ItemResponse, ItemRequest 수정"),
      @ApiChangeLog(date = "2025.04.02", author = Author.WISEUNGJAE, issueNumber = 72, description = "게시글 좋아요 등록취소 로직"),
  })
  @Operation(
      summary = "물품 좋아요 등록 및 취소",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (ItemRequest)
          - **`itemId (UUID)`**: 물품 ID
          
          ## 반환값 (ItemResponse)
          - **`item`**: 물품 정보
          - **`isLiked`**: 좋아요 여부 (boolean)
          
          ## 반환값 예시
          ```
          {
            "item": {
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
            "itemPage": null,
            "isLiked": true
          }
          ```
          
          ## 설명
          - 본인이 등록한 물품에는 좋아요 요청이 불가능합니다
          - 이미 좋아요를 누른 물품은 좋아요 취소가 진행됩니다
          """
  )
  ResponseEntity<ItemResponse> postLike(
      CustomUserDetails customUserDetails,
      ItemRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.27", author = Author.KIMNAYOUNG, issueNumber = 538, description = "추천 물품 리스트 조회 시 사용자 반경내 물품만 반환"),
      @ApiChangeLog(date = "2026.02.10", author = Author.SUHSAECHAN, issueNumber = 496, description = "물품 리스트 조회 시 각 Item 내부에 신고 여부(isReported) 플래그 추가"),
      @ApiChangeLog(date = "2026.01.20", author = Author.KIMNAYOUNG, issueNumber = 443, description = "사용자 맞춤형 추천 시스템 추가"),
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "물품 탐색 시, 차단된 회원의 물품을 제외하는 로직 추가"),
      @ApiChangeLog(date = "2025.09.18", author = Author.BAEKJIHOON, issueNumber = 336, description = "물품 필터링 조회 반환값 구조 개선"),
      @ApiChangeLog(date = "2025.08.31", author = Author.KIMNAYOUNG, issueNumber = 299, description = "nativeQuery -> QueryDSL로 변경"),
      @ApiChangeLog(date = "2025.08.20", author = Author.KIMNAYOUNG, issueNumber = 232, description = "물품 정렬 기준 추가 (거리순, 선호 카테고리순)"),
      @ApiChangeLog(date = "2025.08.20", author = Author.WISEUNGJAE, issueNumber = 258, description = "item detail 조립 로직 N+1 문제 Fetch Join 및 ItemDetailAssembler 클래스로 해결"),
      @ApiChangeLog(date = "2025.07.30", author = Author.KIMNAYOUNG, issueNumber = 233, description = "내가 등록한 물품 제외"),
      @ApiChangeLog(date = "2025.06.30", author = Author.SUHSAECHAN, issueNumber = 128, description = "Controller 반환값 ItemRequest, ItemResponse 로 수정"),
      @ApiChangeLog(date = "2025.05.29", author = Author.KIMNAYOUNG, issueNumber = 128, description = "물품 리스트"),
  })
  @Operation(
      summary = "물품 리스트 조회",
      description = """
          ## 인증(JWT): **필요**

          ## 요청 파라미터 (ItemRequest)
          - **`pageNumber`**: 페이지 번호
          - **`pageSize`**: 페이지 크기
          - **`sortField`**: 정렬 기준 (CREATED_DATE | DISTANCE | PREFERRED_CATEGORY)
          - **`sortDirection`**: 정렬 방향
          - **`radiusInMeters`**: 반경 (m단위, DISTANCE 정렬 시 필수)

          ## 반환값 (ItemResponse)
          - **`Page<Item>`** - 각 Item 내부에 `isReported` (boolean) 포함: 현재 사용자가 해당 물품을 신고했는지 여부

          ## 반환값 예시
          ```
          {
             "item": null,
             "itemPage": {
               "content": [
                 {
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
                 {
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
                   "isAiPredictedPrice": false,
                   "longitude": 125.86524143885734,
                   "latitude": 33.33203387166089
                 }
               ],
               "page": {
                 "size": 2,
                 "number": 0,
                 "totalElements": 21,
                 "totalPages": 11
               }
             },
             "isLiked": null
           }
          ```
          
          ## 반환값 설명
          - Spring에서 제공하는 Page<> 형태 사용
          - Page<> 내부에 데이터 반환
          - 페이지네이션 된 Item 객체를 반환하며, 각각의 Item 내부에는 ItemImage, Member 데이터가 반환됨
          
          ## 설명 
          - 내가 등록한 물품은 제외하고 물품 리스트 조회
          - CREATED_DATE / DESC : 최신순으로 정렬된 물품 리스트
          - DISTANCE / ASC : 거리 가까운 순으로 정렬된 물품 리스트
          - PREFERRED_CATEGORY / ASC : 선호 카테고리와 물품 간의 유사도가 높은 순으로 정렬된 물품 리스트
          - RECOMMENDED / ASC : 추천 순으로 정렬된 물품 리스트
          - sortField, sortDirection이 null인 경우 기본값은 CREATED_DATE, DESC
          
          ## 에러코드
          - **`INVALID_SOCIAL_TOKEN`**: 유효하지 않은 소셜 인증 토큰입니다.
          - **`SOCIAL_AUTH_FAILED`**: 소셜 로그인 인증에 실패하였습니다.
          - **`MEMBER_NOT_FOUND`**: 회원 정보를 찾을 수 없습니다.
          """
  )
  ResponseEntity<ItemResponse> getItemList(
      CustomUserDetails customUserDetails,
      ItemRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.10", author = Author.SUHSAECHAN, issueNumber = 498, description = "물품 상세 조회 시 차단 여부(isBlocked), 신고 여부(isReported) 플래그 추가"),
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "차단된 회원의 물품 조회 방지 로직 추가"),
      @ApiChangeLog(date = "2025.09.18", author = Author.BAEKJIHOON, description = "물품 상세 조회 반환값 구조 개선"),
      @ApiChangeLog(date = "2025.08.18", author = Author.WISEUNGJAE, description = "물품 상세 조회 시 회원 위도 경도 반환 추가"),
      @ApiChangeLog(date = "2025.07.08", author = Author.KIMNAYOUNG, issueNumber = 192, description = "물품 상세 조회"),
  })
  @Operation(
      summary = "물품 상세 조회",
      description = """
          ## 인증(JWT): **필요**

          ## 요청 파라미터 (ItemRequest)
          - **`itemId (UUID)`**: 물품 ID

          ## 반환값 (ItemResponse)
          - **`item`**: 물품 (내부에 `isBlocked`, `isReported` 포함)
          - **`isLiked`**: 좋아요 여부 (boolean)
          - Item 내부 필드:
            - **`isBlocked`**: 차단 여부 (boolean) - 현재 사용자와 물품 등록자 간 차단 여부
            - **`isReported`**: 신고 여부 (boolean) - 현재 사용자가 이 물품을 신고했는지 여부

          ## 반환값 예시
          ```
          {
            "item": {
              "createdDate": "2025-09-18T13:40:36.478223",
              "updatedDate": "2025-09-18T13:40:36.478223",
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
                "latitude": 56.900000000000006,
                "longitude": 123.1
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
                }
              ],
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
              "isBlocked": false,
              "isReported": false,
              "longitude": 0.1,
              "latitude": 0.1
            },
            "itemPage": null,
            "isLiked": false
          }
          ```

          ## 반환값 설명
          - Item 데이터를 반환하며, 내부에 ItemImage(물품 이미지), Member(물품 등록 사용자 정보)를 추가적으로 반환
          - 로그인된 사용자가 조회한 물품에 **좋아요** 를 눌렀는지 여부는 `Boolean isLiked`로 확인 가능
          - `item.isBlocked`가 true이면 차단된 회원의 물품이므로 "요청하기" 버튼을 비활성화해야 함
          - `item.isReported`가 true이면 이미 신고한 물품
          """
  )
  ResponseEntity<ItemResponse> getItemDetail(
      CustomUserDetails customUserDetails,
      ItemRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.06.27", author = Author.KIMNAYOUNG, issueNumber = 155, description = "물품 가격 예측"),
  })
  @Operation(
      summary = "AI 기반 물품 가격 예측",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (ItemRequest)
          - **`itemName`**: 물품명
          - **`itemDescription`**: 물품 상세 설명
          - **`itemCondition`**: 물품 상태
          
          ## 반환값 (Integer)
          - 예측된 가격 (KRW, 정수)
          """
  )
  ResponseEntity<Integer> getItemPrice(CustomUserDetails customUserDetails, ItemRequest request);


  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.09.18", author = Author.BAEKJIHOON, issueNumber = 336, description = "물품 수정 api 반환값 제거"),
      @ApiChangeLog(date = "2025.07.25", author = Author.KIMNAYOUNG, issueNumber = 234, description = "거래 희망 위치 추가"),
      @ApiChangeLog(date = "2025.06.26", author = Author.WISEUNGJAE, issueNumber = 156, description = "물품 수정 로직 생성"),
  })
  @Operation(
      summary = "물품 수정",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (ItemRequest)
          - **`member`**: 회원
          - **`itemImageUrls`**: 물품 사진 URL (물품 전체 사진)
          - **`itemName`**: 물품명
          - **`itemDescription`**: 물품 상세 설명
          - **`itemCategory`**: 물품 카테고리
          - **`itemCondition`**: 물품 상태
          - **`itemTradeOptions`**: 물품 옵션
          - **`itemPrice`**: 가격
          - **`itemCustomTags`**: 커스텀 태그
          - **`longitude`**: 거래 희망 위치 경도
          - **`latitude`**: 거래 희망 위치 위도
          - **`itemId (UUID)`**: 물품 ID
          - **`aiPredictedPrice (boolean)`**: AI 가격측정 여부
          
          ## 반환값
          `없음`
          """
  )
  ResponseEntity<Void> updateItem(
      CustomUserDetails customUserDetails,
      ItemRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.06.26", author = Author.WISEUNGJAE, issueNumber = 156, description = "물품 삭제 로직 생성"),
  })
  @Operation(
      summary = "물품 삭제",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (ItemRequest)
          - **`member`**: 회원
          - **`itemId (UUID)`**: 물품 ID
          
          ## 반환값
          - 성공 시 상태코드 200 (OK)와 빈 응답 본문
          
          ## 에러코드
          - **`ITEM_NOT_FOUND`**: 물품을 찾을 수 없습니다.
          - **`UNAUTHORIZED`**: 물품 소유자가 아닙니다.
          """
  )
  ResponseEntity<ItemResponse> deleteItem(CustomUserDetails customUserDetails, ItemRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.09.18", author = Author.BAEKJIHOON, issueNumber = 336, description = "반환값 구조 개선"),
      @ApiChangeLog(date = "2025.09.07", author = Author.WISEUNGJAE, issueNumber = 310, description = "ITEMSTATUS 설명 추가, 이슈 번호 수정 등 docs 수정"),
      @ApiChangeLog(date = "2025.08.31", author = Author.KIMNAYOUNG, issueNumber = 299, description = "nativeQuery -> QueryDSL로 변경"),
      @ApiChangeLog(date = "2025.08.20", author = Author.WISEUNGJAE, issueNumber = 258, description = "item detail 조립 로직 N+1 문제 Fetch Join 및 ItemDetailAssembler 클래스로 해결"),
      @ApiChangeLog(date = "2025.07.08", author = Author.BAEKJIHOON, issueNumber = 206, description = "내가 등록한 물품 조회 API"),
  })
  @Operation(
      summary = "내가 등록한 물품 조회",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (ItemRequest)
          - **`itemStatus`**: 물품 거래 상태 (AVAILABLE : 교환 가능한 상태, EXCHANGED : 교환 완료된 상태)
          - **`pageNumber`**: 인덱스 번호
          - **`pageSize`**: 한 페이지에 반환할 데이터 개수
          
          ## 반환값 (ItemResponse)
          - **`Page<Item>`: 페이지네이션된 물품
          
          ## 반환값 예시
          ```
          {
            "item": null,
            "itemPage": {
              "content": [
                {
                  "createdDate": "2025-09-18T14:59:59.563502",
                  "updatedDate": "2025-09-18T14:59:59.563502",
                  "itemId": "bb0841ca-8902-4168-a6fa-d02c0293cfe6",
                  "member": {
                    "createdDate": "2025-09-18T13:41:48.341489",
                    "updatedDate": "2025-09-18T14:03:05.587153",
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
                    "isMemberLocationSaved": false,
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
                  "createdDate": "2025-09-18T14:07:43.01203",
                  "updatedDate": "2025-09-18T14:07:43.01203",
                  "itemId": "b2bf9c81-4844-4b71-a67b-8ee7068cf745",
                  "member": {
                    "createdDate": "2025-09-18T13:41:48.341489",
                    "updatedDate": "2025-09-18T14:03:05.587153",
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
                    "isMemberLocationSaved": false,
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
                "size": 2,
                "number": 0,
                "totalElements": 5,
                "totalPages": 3
              }
            },
            "isLiked": null
          }
          ```
          
          ## 설명
          - `itemStatus`를 지정하지 않으면 상태값에 대한 필터링 없이 사용자가 등록한 모든 물품을 조회합니다
          """
  )
  ResponseEntity<ItemResponse> getMyItems(
      CustomUserDetails customUserDetails,
      ItemRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.01.03", author = Author.WISEUNGJAE, issueNumber = 428, description = "차단된 회원의 물품 조회 방지 로직 추가"),
      @ApiChangeLog(date = "2025.10.29", author = Author.KIMNAYOUNG, issueNumber = 373, description = "좋아요 목록 리스트"),
  })
  @Operation(
      summary = "좋아요 목록 리스트 조회",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (ItemRequest)
          - **`pageNumber`**: 인덱스 번호
          - **`pageSize`**: 한 페이지에 반환할 데이터 개수
          
          ## 반환값 (ItemResponse)
          - **`Page<Item>`: 페이지네이션된 물품
          """
  )
  ResponseEntity<ItemResponse> getLikedItems(
      CustomUserDetails customUserDetails,
      ItemRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.09.07", author = Author.WISEUNGJAE, issueNumber = 310, description = "ITEMSTATUS 설명 추가, 이슈 번호 수정 등 docs 수정"),
      @ApiChangeLog(date = "2025.08.01", author = Author.WISEUNGJAE, issueNumber = 231, description = "내가 등록한 물품 거래 상태 변경 기능 추가")
  })
  @Operation(
      summary = "물품 거래 상태 변경 API",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (ItemRequest)
          - **`itemStatus`**: 물품 거래 상태 (AVAILABLE : 교환 가능한 상태, EXCHANGED : 교환 완료된 상태)
          - **`itemId (UUID)`**: 물품 ID
          
          ## 반환값 (ItemResponse)
          - **`item`**: 물품
          - **`isLiked`**: 좋아요 여부 (boolean)
          """
  )
  ResponseEntity<ItemResponse> updateTradeStatus(
      CustomUserDetails customUserDetails,
      ItemRequest request
  );
}
