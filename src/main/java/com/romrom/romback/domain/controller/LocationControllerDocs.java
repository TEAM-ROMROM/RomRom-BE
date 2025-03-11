package com.romrom.romback.domain.controller;

import com.romrom.romback.domain.object.constant.Author;
import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.dto.LocationRequest;
import com.romrom.romback.global.docs.ApiChangeLog;
import com.romrom.romback.global.docs.ApiChangeLogs;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;

public interface LocationControllerDocs {

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
      `없음`
      
      ## 에러코드
      - **`INVALID_REQUEST`**: 잘못된 입력값이 요청되었습니다.
    """
  )
  ResponseEntity<Void> saveLocation(
      @AuthenticationPrincipal CustomUserDetails customUserDetails,
      @ModelAttribute LocationRequest request);
}
