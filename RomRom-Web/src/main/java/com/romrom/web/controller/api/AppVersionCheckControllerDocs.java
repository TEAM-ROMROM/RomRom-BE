package com.romrom.web.controller.api;

import com.romrom.common.dto.Author;
import com.romrom.web.dto.SystemRequest;
import com.romrom.web.dto.SystemResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AppVersionCheckControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.03.12", author = Author.BAEKJIHOON, issueNumber = 566, description = "앱 버전 체크 API 구현 - 강제 업데이트 및 권장 업데이트 여부 반환"),
  })
  @Operation(
      summary = "앱 버전 체크",
      description = """
      ## 인증(JWT): **불필요**

      ## 요청 파라미터 (SystemRequest)
      - **`appVersion`**: 현재 앱 버전 (예: 1.3.0)
      - **`platform`**: 플랫폼 (IOS, ANDROID)

      ## 반환값 (SystemResponse)
      - **`forceUpdate`**: 강제 업데이트 필요 여부 (현재 버전 < 최소 필수 버전)
      - **`recommendUpdate`**: 권장 업데이트 여부 (최소 버전 이상 & 최신 버전 미만)
      - **`latestVersion`**: 현재 최신 버전 (SystemConfig app.latest.version)
      - **`storeUrl`**: 플랫폼에 맞는 스토어 URL (SystemConfig app.store.ios / app.store.android)

      ## 동작 설명
      - `forceUpdate = true`: 즉시 업데이트 강제 (스토어로 이동)
      - `recommendUpdate = true`: 업데이트 권장 (스킵 가능)
      - 두 값 모두 `false`: 정상 사용 가능
      - SystemConfig에 버전 미설정 시 모든 값 `false` 반환 (안전 처리)

      ## 에러코드
      - 별도 에러코드 없음 (미설정 시 false 반환)
      """
  )
  ResponseEntity<SystemResponse> checkVersion(SystemRequest request);
}
