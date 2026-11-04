package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

/**
 * 물품 검색 API 문서 (ItemControllerDocs 분리, #862)
 */
public interface ItemSearchControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 862, description = "물품 검색 (검색어·카테고리 필터·정렬 4종) 신규"),
  })
  @Operation(
      summary = "물품 검색",
      description = """
          ## 인증(JWT): **필요**

          ## 요청 파라미터 (ItemRequest, multipart/form-data)
          - **`searchKeyword`** (필수): 검색어. 앞뒤 공백 제거 후 1자 이상, 50자 초과분은 잘라냄. 물품명·설명에서 대소문자 무시 부분 일치
          - **`searchItemCategories`** (선택): 카테고리 필터 (쉼표 구분, 예: `SHOES,BEAUTY`). 미전송 > 전체
          - **`searchSortField`** (선택): `LATEST`(기본, 최신순), `DISTANCE`(거리순, 회원 위치 기준·위치 미등록 시 최신순), `PRICE_ASC`(낮은 가격순), `PRICE_DESC`(높은 가격순). 동률은 최신순
          - **`pageNumber`** (선택, 기본 0), **`pageSize`** (선택, 기본 30, 1~50으로 보정)

          ## 노출 조건 (홈 피드와 동일)
          - 본인 물품·삭제·관리자 노출 차단·교환 완료 물품 제외, 정지 회원 물품 제외, 상호 차단 회원 물품 제외, 다시 보지 않기 물품 제외
          - 추가: 내가 신고한 물품 제외
          - 반경 제한 없음 (전체 지역 검색)

          ## 반환값 (ItemResponse)
          - **`itemPage`**: 검색 결과 페이지 (Spring Page, `content`의 각 item은 물품 목록 조회와 같은 형태)

          ## 에러코드
          - **`INVALID_SEARCH_KEYWORD`**: 검색어가 비었거나 공백만 있음 (400)
          """
  )
  ResponseEntity<ItemResponse> searchItems(
      CustomUserDetails customUserDetails,
      ItemRequest request
  );
}
