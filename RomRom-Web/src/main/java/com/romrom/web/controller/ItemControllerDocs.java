package com.romrom.web.controller;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.object.Author;
import com.romrom.item.dto.ItemDetailResponse;
import com.romrom.item.dto.ItemFilteredRequest;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import com.romrom.item.dto.LikeRequest;
import com.romrom.item.dto.LikeResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.data.domain.Page;
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

  @ApiChangeLogs({
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
      
      ## 요청 파라미터 (ItemFilteredRequest)
      - **`page`**: 페이지 번호
      - **`size`**: 페이지 크기
      
      ## 반환값 (Page<ItemResponse>)
      - **`item`**: 물품
      - **`itemImages`**: 물품 사진
      - **`itemCustomTags`**: 커스텀 태그
      """
  )
  ResponseEntity<Page<ItemDetailResponse>> getItem(CustomUserDetails customUserDetails, ItemFilteredRequest request);
}
