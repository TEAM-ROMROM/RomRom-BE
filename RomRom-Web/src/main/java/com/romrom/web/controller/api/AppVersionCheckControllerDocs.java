package com.romrom.web.controller.api;

import com.romrom.common.dto.Author;
import com.romrom.web.dto.SystemResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AppVersionCheckControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.03.12", author = Author.BAEKJIHOON, issueNumber = 566, description = "앱 버전 체크 API 구현 - SystemConfig 버전 설정값 조회 반환"),
  })
  @Operation(
      summary = "앱 버전 체크",
      description = """
      ## 인증(JWT): **불필요**

      ## 요청 파라미터
      - **없음** (파라미터 전송 불필요)

      ## 반환값 (SystemResponse)
      - **`minimumVersion`**: 앱 최소 필수 버전. 이 버전 미만이면 강제 업데이트 필요 (SystemConfig app.min.version)
      - **`latestVersion`**: 현재 최신 버전 (SystemConfig app.latest.version)
      - **`androidStoreUrl`**: Android Google Play URL (SystemConfig app.store.android)
      - **`iosStoreUrl`**: iOS App Store URL (SystemConfig app.store.ios)

      ## 동작 설명
      - BE는 SystemConfig에서 4개 값만 조회하여 반환
      - 버전 비교 및 강제/권장 업데이트 판단은 클라이언트에서 처리
      - SystemConfig에 설정값이 없으면 빈 문자열 반환

      ## 에러코드
      - 별도 에러코드 없음
      """
  )
  ResponseEntity<SystemResponse> checkVersion();
}
