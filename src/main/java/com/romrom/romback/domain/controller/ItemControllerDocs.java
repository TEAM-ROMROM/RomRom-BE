package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.constant.Author;
import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.dto.ItemRequest;
import com.romrom.romback.domain.object.dto.ItemResponse;
import com.romrom.romback.global.docs.ApiChangeLog;
import com.romrom.romback.global.docs.ApiChangeLogs;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;

public interface ItemControllerDocs {

  @ApiChangeLogs({
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
      - **`images`**: 상품 사진
      - **`itemName`**: 상품명
      - **`itemDescription`**: 상품 상세 설명
      - **`itemCategory`**: 상품 카테고리
      - **`itemCondition`**: 상품 상태
      - **`tradeOptions`**: 상품 옵션
      - **`price`**: 가격
      
      ## 반환값 (ItemResponse)
      - **`member`**: 회원
      - **`item`**: 상품
      - **`itemImages`**: 상품 사진
      """
  )
  ResponseEntity<ItemResponse> postItem(CustomUserDetails customUserDetails, ItemRequest request);
}
