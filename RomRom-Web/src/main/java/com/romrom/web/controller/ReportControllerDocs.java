package com.romrom.web.controller;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.report.dto.ItemReportRequest;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;
public interface ReportControllerDocs {


  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.07.09",
          author = Author.WISEUNGJAE,
          issueNumber = 195,
          description = "물품 신고 API 구현"
      )
  })
  @Operation(
      summary = "아이템 신고",
      description = """
      ## 인증(JWT): **필요**
      
      ## 요청 파라미터 (ReportRequest)
      - **`itemId`**: 물품 Id
      - **`reasons`**: 신고 사유 코드들
      - **`extraComment`**: 기타 입력 내용
      
      ## 에러코드
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      - **`DUPLICATE_REPORT`**: 같은 물품을 여러 번 신고할 수 없습니다.
      - **`TOO_LONG_EXTRA_COMMENT`**: 기타 의견의 글자 수 제한을 초과했습니다.
      - **`NULL_EXTRA_COMMENT`**: 기타 의견을 빈 값으로 요청할 수 없습니다.
      - **`MEMBER_NOT_FOUND`**: 회원을 찾을 수 없습니다.
      """
  )
  public ResponseEntity<Void> reportItem(CustomUserDetails customUserDetails, ItemReportRequest request);
  }
