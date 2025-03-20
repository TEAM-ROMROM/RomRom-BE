package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.constant.Author;
import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.dto.MemberRequest;
import com.romrom.romback.global.docs.ApiChangeLog;
import com.romrom.romback.global.docs.ApiChangeLogs;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;

public interface MemberControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.03.04",
          author = Author.SUHSAECHAN,
          issueNumber = 32,
          description = "파라미터 수정: memberProductCategories -> preferredCategories"
      ),
      @ApiChangeLog(
          date = "2025.02.23",
          author = Author.SUHSAECHAN,
          issueNumber = 32,
          description = "회원 선호 카테고리 저장 API 추가"
      )
  })
  @Operation(
      summary = "회원 선호 카테고리 저장",
      description = """
      ## 인증(JWT): **필요**
      
      ## 요청 파라미터 (MemberRequest)
      - **`preferredCategories`**: 회원이 선호하는 상품 카테고리 리스트 (List<Integer>)
      
      ## 반환값
      - HTTP 상태 코드 201 (CREATED): 요청이 성공적으로 처리됨
      
      ## 에러코드
      - **`INVALID_MEMBER`**: 유효하지 않은 회원 정보입니다.
      - **`INVALID_CATEGORY_CODE`**: 존재하지 않는 카테고리 코드입니다.
    """
  )
  ResponseEntity<Void> saveMemberProductCategories(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.03.11",
          author = Author.BAEKJIHOON,
          issueNumber = 50,
          description = "사용자 위치인증 데이터 저장"
      )
  })
  @Operation(
      summary = "사용자 위치 데이터 저장",
      description = """
      ## 인증(JWT): **필요**
      
      ## 요청 파라미터 (MemberRequest)
      - **`longitude`**: 경도 (double)
      - **`latitude`**: 위도도 (double)
      - **`siDo`**: 시/도 (String)
      - **`siGunGu`**: 시/군/구 (String)
      - **`eupMyoenDong`**: 읍/면/동 (String)
      - **`ri`**: 리 (String)
      - **`fullAddress`**: 지번 주소 (String)
      - **`roadAddress`**: 도로명 주소 (String)
      
      ## 반환값
      성공시 : 201 CREATED
      
      ## 에러코드
      - **`INVALID_REQUEST`**: 잘못된 입력값이 요청되었습니다.
    """
  )
  ResponseEntity<Void> saveMemberLocation(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute MemberRequest request);
}
