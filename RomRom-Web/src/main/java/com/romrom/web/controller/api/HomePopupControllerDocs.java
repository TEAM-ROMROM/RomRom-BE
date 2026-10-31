package com.romrom.web.controller.api;

import com.romrom.common.dto.Author;
import com.romrom.notification.dto.HomePopupResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface HomePopupControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 749, description = "앱 홈 활성 팝업 조회 API 추가"),
  })
  @Operation(
      summary = "활성 홈 팝업 조회",
      description = """
          ## 인증(JWT): **불필요** (비로그인 포함 누구나 호출)

          ## 요청 파라미터
          - 없음 (`GET /api/home-popups/active`)

          ## 반환값 (HomePopupResponse)
          - **`homePopup`**: 지금 노출할 팝업 1건, 없으면 `null`
            - 선택 기준: `isActive = true` 이고 현재 시각이 `startDate ~ endDate` 안 (null 경계는 제한 없음)
            - 여러 건이면 `priority` 큰 순, 같으면 최근 등록순으로 1건

          ## 응답 예시 (팝업 있음)
          ```json
          {
            "homePopup": {
              "createdDate": "2026-10-31T10:00:00",
              "updatedDate": "2026-10-31T10:00:00",
              "homePopupId": "6f1c2b9a-3e2d-4c55-9a43-1f0a7c2d8e11",
              "title": "롬롬 가을 이벤트",
              "description": "교환 완료 시 추첨으로 선물을 드려요",
              "imageUrl": "https://cdn.example.com/romrom/popup.webp",
              "linkUrl": "romrom://event/autumn",
              "priority": 10,
              "isActive": true,
              "startDate": "2026-10-31T00:00:00",
              "endDate": "2026-11-07T23:59:00"
            }
          }
          ```

          ## 응답 예시 (팝업 없음)
          ```json
          { "homePopup": null }
          ```

          ## 필드 설명
          - **`linkUrl`**: `https://` 외부 링크 또는 `romrom://` 앱 딥링크, 없으면 null (자세히 알아보기 버튼 미노출)
          - **`startDate` / `endDate`**: ISO-8601 LocalDateTime, null이면 해당 경계 제한 없음
          """
  )
  ResponseEntity<HomePopupResponse> getActiveHomePopup();
}
