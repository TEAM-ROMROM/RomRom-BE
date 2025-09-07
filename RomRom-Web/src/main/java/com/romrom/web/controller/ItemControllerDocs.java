package com.romrom.web.controller;

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
      @ApiChangeLog(
          date = "2025.08.08",
          author = Author.BAEKJIHOON,
          issueNumber = 262,
          description = "물품 등록 시 AI 가격 측정 여부 저장 및 반환"
      ),
      @ApiChangeLog(
          date = "2025.08.03",
          author = Author.KIMNAYOUNG,
          issueNumber = 243,
          description = "이미지 업로드 워크플로우 개선"
      ),
      @ApiChangeLog(
          date = "2025.07.25",
          author = Author.KIMNAYOUNG,
          issueNumber = 234,
          description = "거래 희망 위치 추가"
      ),
      @ApiChangeLog(
          date = "2025.03.22",
          author = Author.WISEUNGJAE,
          issueNumber = 60,
          description = "물품 등록 로직에 커스텀 태그 등록 로직 추가"
      ),
      @ApiChangeLog(
          date = "2025.03.15",
          author = Author.KIMNAYOUNG,
          issueNumber = 55,
          description = "물품 등록 로직 생성"
      )
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
          - **`aiPrice`**: AI 가격측정 여부
          
          ## 반환값 (ItemResponse)
          - **`item`**: 물품
          - **`itemImages`**: 물품 사진
          - **`itemCustomTags`**: 커스텀 태그
          """
  )
  ResponseEntity<ItemResponse> postItem(CustomUserDetails customUserDetails, ItemRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.06.30",
          author = Author.SUHSAECHAN,
          issueNumber = 72,
          description = "반환값 요청값 ItemResponse, ItemRequest 수정"
      ),
      @ApiChangeLog(
          date = "2025.04.02",
          author = Author.WISEUNGJAE,
          issueNumber = 72,
          description = "게시글 좋아요 등록취소 로직"
      )
  })
  @Operation(
      summary = "물품 좋아요 등록 및 취소",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (ItemRequest)
          - **`itemId (UUID)`**: 물품 ID
          
          ## 반환값 (ItemResponse)
          - **`item`**: 물품 정보
          - **`likeStatus`**: 좋아요 상태 (LIKE/UNLIKE)
          - **`likeCount`**: 좋아요 개수
          """
  )
  ResponseEntity<ItemResponse> postLike(CustomUserDetails customUserDetails, ItemRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.08.20",
          author = Author.KIMNAYOUNG,
          issueNumber = 232,
          description = "물품 정렬 기준 추가 (거리순, 선호 카테고리순)"
      ),
      @ApiChangeLog(
          date = "2025.08.20",
          author = Author.WISEUNGJAE,
          issueNumber = 258,
          description = "item detail 조립 로직 N+1 문제 Fetch Join 및 ItemDetailAssembler 클래스로 해결"
      ),
      @ApiChangeLog(
          date = "2025.07.30",
          author = Author.KIMNAYOUNG,
          issueNumber = 233,
          description = "내가 등록한 물품 제외"
      ),
      @ApiChangeLog(
          date = "2025.06.30",
          author = Author.SUHSAECHAN,
          issueNumber = 128,
          description = "Controller 반환값 ItemRequest, ItemResponse 로 수정"
      ),
      @ApiChangeLog(
          date = "2025.05.29",
          author = Author.KIMNAYOUNG,
          issueNumber = 128,
          description = "물품 리스트"
      )
  })
  @Operation(
      summary = "물품 리스트 조회",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (ItemRequest)
          - **`pageNumber`**: 페이지 번호
          - **`pageSize`**: 페이지 크기
          - **`sortType`**: 정렬 기준
          - **`sortDirection`**: 정렬 방향
          - **`radiusInMeters`**: 반경 (m단위)
          
          ## 반환값 (ItemResponse)
          - **`itemDetailPage`**: 페이지네이션된 물품 상세 정보
            - **`itemId`**: 물품 ID
            - **`memberId`**: 회원 ID
            - **`profileUrl`**: 프로필 사진 URL
            - **`itemName`**: 물품명
            - **`itemDescription`**: 물품 상세 설명
            - **`itemCategory`**: 물품 카테고리
            - **`itemCondition`**: 물품 상태
            - **`itemTradeOptions`**: 물품 옵션
            - **`likeCount`**: 좋아요 수
            - **`price`**: 가격
            - **`createdDate`**: 생성일
            - **`imageUrls`**: 이미지 URL 목록
            - **`itemCustomTags`**: 커스텀 태그 목록
            - **`longitude`**: 거래 희망 위치 경도
            - **`latitude`**: 거래 희망 위치 위도
          """
  )
  ResponseEntity<ItemResponse> getItemList(CustomUserDetails customUserDetails, ItemRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.08.18",
          author = Author.WISEUNGJAE,
          description = "물품 상세 조회 시 회원 위도 경도 반환 추가"
      ),
      @ApiChangeLog(
          date = "2025.07.08",
          author = Author.KIMNAYOUNG,
          issueNumber = 192,
          description = "물품 상세 조회"
      )
  })
  @Operation(
      summary = "물품 상세 조회",
      description = """
          ## 인증(JWT): **필요**
          
          ## 요청 파라미터 (ItemRequest)
          - **`itemId (UUID)`**: 물품 ID
          
          ## 반환값 (ItemResponse)
          - **`item`**: 물품
          - **`itemImages`**: 물품 사진
          - **`itemCustomTags`**: 커스텀 태그
          - **`likeStatus`**: 좋아요 상태 (LIKE/UNLIKE)
          """
  )
  ResponseEntity<ItemResponse> getItemDetail(CustomUserDetails customUserDetails, ItemRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.06.27",
          author = Author.KIMNAYOUNG,
          issueNumber = 155,
          description = "물품 가격 예측"
      )
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
      @ApiChangeLog(
          date = "2025.07.25",
          author = Author.KIMNAYOUNG,
          issueNumber = 234,
          description = "거래 희망 위치 추가"
      ),
      @ApiChangeLog(
          date = "2025.06.26",
          author = Author.WISEUNGJAE,
          issueNumber = 156,
          description = "물품 수정 로직 생성"
      )
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
          - **`aiPrice (boolean)`**: AI 가격측정 여부
          
          ## 반환값 (ItemResponse)
          - **`member`**: 회원
          - **`item`**: 물품
          - **`itemImages`**: 물품 사진
          - **`itemCustomTags`**: 커스텀 태그
          """
  )
  ResponseEntity<ItemResponse> updateItem(CustomUserDetails customUserDetails, ItemRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.06.26",
          author = Author.WISEUNGJAE,
          issueNumber = 156,
          description = "물품 삭제 로직 생성"
      )
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
      @ApiChangeLog(
          date = "2025.09.07",
          author = Author.WISEUNGJAE,
          issueNumber = 310,
          description = "ITEMSTATUS 설명 추가, 이슈 번호 수정 등 docs 수정"
      ),
      @ApiChangeLog(
          date = "2025.08.20",
          author = Author.WISEUNGJAE,
          issueNumber = 258,
          description = "item detail 조립 로직 N+1 문제 Fetch Join 및 ItemDetailAssembler 클래스로 해결"
      ),
      @ApiChangeLog(
          date = "2025.07.08",
          author = Author.BAEKJIHOON,
          issueNumber = 206,
          description = "내가 등록한 물품 조회 API"
      )
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
          - **`Page<ItemDetail>`: 페이지네이션된 물품 상세 정보
          """
  )
  ResponseEntity<ItemResponse> getMyItems(CustomUserDetails customUserDetails, ItemRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.09.07",
          author = Author.WISEUNGJAE,
          issueNumber = 310,
          description = "ITEMSTATUS 설명 추가, 이슈 번호 수정 등 docs 수정"
      ),
      @ApiChangeLog(
      date = "2025.08.01",
      author = Author.WISEUNGJAE,
      issueNumber = 231,
      description = "내가 등록한 물품 거래 상태 변경 기능 추가"
    )
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
      - **`itemImages`**: 물품 사진
      - **`itemCustomTags`**: 커스텀 태그
      - **`likeStatus`**: 좋아요 상태 (LIKE/UNLIKE)
      - **`likeCount`**: 좋아요 개수
      """
  )
  ResponseEntity<ItemResponse> updateTradeStatus(CustomUserDetails customUserDetails, ItemRequest request);
}
