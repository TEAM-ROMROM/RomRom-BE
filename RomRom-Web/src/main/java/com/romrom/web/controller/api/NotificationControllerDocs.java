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
      @ApiChangeLog(
          date = "2025.07.22",
          author = Author.KIMNAYOUNG,
          issueNumber = 228,
          description = "알림 기능 구현"
      )
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
      """
  )
  ResponseEntity<Void> saveFcmToken(CustomUserDetails customUserDetails, NotificationRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.07.22",
          author = Author.KIMNAYOUNG,
          issueNumber = 228,
          description = "알림 기능 구현"
      )
  })
  @Operation(
      summary = "특정 회원 알림 발송",
      description = """
      ## 인증(JWT): **필요**
      
      ## 요청 파라미터 (NotificationRequest)
      - **`title`**: 제목
      - **`body`**: 내용
      - **`memberIdList`**: 알림을 발송할 회원 ID 리스트
      
      ## 반환값
      - 성공 시 상태코드 200 (OK)와 빈 응답 본문
      
      ## 에러코드
      - **`MEMBER_NOT_FOUND`**: 회원 정보를 찾을 수 없습니다.
      - **`NOTIFICATION_FAILED`**: 알림 발송에 실패했습니다.
      """
  )
  ResponseEntity<Void> sendToMembers(CustomUserDetails customUserDetails, NotificationRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(
          date = "2025.07.22",
          author = Author.KIMNAYOUNG,
          issueNumber = 228,
          description = "알림 기능 구현"
      )
  })
  @Operation(
      summary = "전체 회원 알림 발송",
      description = """
      ## 인증(JWT): **필요**
      
      ## 요청 파라미터 (NotificationRequest)
      - **`title`**: 제목
      - **`body`**: 내용
      
      ## 반환값
      - 성공 시 상태코드 200 (OK)와 빈 응답 본문
      
      ## 에러코드
      - **`NOTIFICATION_FAILED`**: 알림 발송에 실패했습니다.
      """
  )
  ResponseEntity<Void> sendToAll(CustomUserDetails customUserDetails, NotificationRequest request);
}
