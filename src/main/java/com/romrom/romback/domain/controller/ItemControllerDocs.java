package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.constant.Author;
import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.dto.ItemRequest;
import com.romrom.romback.domain.object.dto.ItemResponse;
import com.romrom.romback.domain.object.dto.LikeRequest;
import com.romrom.romback.domain.object.dto.LikeResponse;
import com.romrom.romback.global.docs.ApiChangeLog;
import com.romrom.romback.global.docs.ApiChangeLogs;
import io.swagger.v3.oas.annotations.Operation;
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
      - **`images`**: 상품 사진
      - **`itemName`**: 상품명
      - **`itemDescription`**: 상품 상세 설명
      - **`itemCategory`**: 상품 카테고리
      - **`itemCondition`**: 상품 상태
      - **`itemTradeOptions`**: 상품 옵션
      - **`itemPrice`**: 가격
      - **`itemCustomTags`**: 커스텀 태그
      
      ## 반환값 (ItemResponse)
      - **`member`**: 회원
      - **`item`**: 상품
      - **`itemImages`**: 상품 사진
      - **`itemCustomTags`**: 커스텀 태그
      """
  )
  ResponseEntity<ItemResponse> postItem(CustomUserDetails customUserDetails, ItemRequest request);

  @ApiChangeLogs({
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
      
      ## 요청 파라미터 (LikeRequest)
      - **`itemId (UUID)`**: 물품 ID
      
      ## 반환값 (LikeResponse)
      - **`likeStatusEnum`**: 좋아요 등록 유무
      - **`likeCount`**: 좋아요 개수
      """
  )
  ResponseEntity<LikeResponse> postLike(CustomUserDetails customUserDetails, LikeRequest request);
}
