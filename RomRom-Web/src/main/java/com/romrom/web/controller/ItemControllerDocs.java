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
      - **`images`**: 물품 사진
      - **`itemName`**: 물품명
      - **`itemDescription`**: 물품 상세 설명
      - **`itemCategory`**: 물품 카테고리
      - **`itemCondition`**: 물품 상태
      - **`itemTradeOptions`**: 물품 옵션
      - **`itemPrice`**: 가격
      - **`itemCustomTags`**: 커스텀 태그
      
      ## 반환값 (ItemResponse)
      - **`member`**: 회원
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
      """
  )
  ResponseEntity<ItemResponse> getItemList(CustomUserDetails customUserDetails, ItemRequest request);

  @ApiChangeLogs({
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
          date = "2025.07.09",
          author = Author.KIMNAYOUNG,
          issueNumber = 193,
          description = "PathVariable로 물품 ID 받도록 수정"
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
      - **`images`**: 물품 사진
      - **`itemName`**: 물품명
      - **`itemDescription`**: 물품 상세 설명
      - **`itemCategory`**: 물품 카테고리
      - **`itemCondition`**: 물품 상태
      - **`itemTradeOptions`**: 물품 옵션
      - **`itemPrice`**: 가격
      - **`itemCustomTags`**: 커스텀 태그
      
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
      
      ## 반환값 (ItemResponse)
      - **`member`**: 회원
      - **`item`**: 물품
      - **`itemImages`**: 물품 사진
      - **`itemCustomTags`**: 커스텀 태그
      """
  )
  ResponseEntity<ItemResponse> deleteItem(CustomUserDetails customUserDetails, ItemRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.07.08",
          author = Author.BAEKJIHOON,
          issueNumber = 206,
          description = "내가 등록한 물품 조회 API"
      )
  })
  @Operation(
      summary = "내가 등록한 물품 조회 API",
      description = """
      ## 인증(JWT): **필요**
      
      ## 요청 파라미터 (ItemRequest)
      - **`pageNumber`**: 인덱스 번호
      - **`pageSize`**: 한 페이지에 반환할 데이터 개수
      
      ## 반환값 (ItemResponse)
      Page<ItemDetail>
      """
  )
  ResponseEntity<ItemResponse> getMyItems(CustomUserDetails customUserDetails, ItemRequest request);
}
