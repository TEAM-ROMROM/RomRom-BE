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
}
