package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.notification.dto.NotificationRequest;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface NotificationControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2025.12.03", author = Author.BAEKJIHOON, issueNumber = 417, description = "FCM 토큰 Postgres 저장 및 로직 개선"),
      @ApiChangeLog(date = "2025.07.22", author = Author.KIMNAYOUNG, issueNumber = 228, description = "알림 기능 구현"),
  })
  @Operation(
      summary = "fcm 토큰 등록",
      description = """
      ## 인증(JWT): **필요**
      
      ## 요청 파라미터 (NotificationRequest)
      - **`fcmToken`**: FCM 토큰
      - **`deviceType`**: 기기 종류
      
      ## 반환값
      - 성공 시 상태코드 200 (OK)와 빈 응답 본문
      
      ## 에러코드
      - **`INVALID_TOKEN`**: 유효하지 않은 FCM 토큰입니다.
      
      ## 유의사항
      - 사용자마다 DeviceType 별로 FCM 토큰 1개씩 저장가능합니다 (iOS, Android, Web)
      - FCM 토큰 자동 만료는 없으며, 갱신 시 새롭게 요청하면 됩니다.
      """
  )
  ResponseEntity<Void> saveFcmToken(CustomUserDetails customUserDetails, NotificationRequest request);
}
