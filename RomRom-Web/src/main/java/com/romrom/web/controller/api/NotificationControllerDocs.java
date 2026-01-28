package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.notification.dto.NotificationHistoryRequest;
import com.romrom.notification.dto.NotificationHistoryResponse;
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

  @ApiChangeLogs({
    @ApiChangeLog(date = "2026.01.28", author = Author.BAEKJIHOON, issueNumber = 441, description = "알림 히스토리 기능 추가")
  })
  @Operation(
    summary = "사용자 알림 목록 조회",
    description = """
      ### 인증(JWT): **필요**
      
      ### 요청 파라미터 (NotificationHistoryRequest)
      - `pageNumber` (int, optional): 페이지 인덱스 (0인경우 1페이지)
        - 기본값: `0`
      - `pageSize` (int, optional): 페이지 크기
        - 기본값: `30`
      
      ### 응답 데이터 (NotificationHistoryResponse)
      - `notificationHistoryPage` (Page<NotificationHistory>): 알림 히스토리 페이지 데이터
        - 정렬: `publishedAt`기준 **내림차순**
      
      ### NotificationHistory 필드
      - `notificationHistoryId` (UUID): 알림 히스토리 ID
      - `notificationType` (NotificationType): 알림 타입
        - 가능한 값: `TRADE_REQUEST_RECEIVED`, `CHAT_MESSAGE_RECEIVED`, `ITEM_LIKED`, `SYSTEM_NOTICE`
      - `title` (String): 알림 제목
      - `body` (String): 알림 본문
      - `payload` (JSON): 알림 Payload
      - `isRead` (Boolean): 읽음 여부
      - `publishedAt` (LocalDateTime): 발송(게시) 시각
      
      ### 사용 방법
      1. 로그인(JWT) 후 알림 목록 조회 API를 호출합니다.
      2. `pageNumber`, `pageSize`로 페이징 조건을 전달합니다.
      3. 응답의 `notificationHistoryPage`에서 최신 알림부터 확인할 수 있습니다.
      
      ### 유의 사항
      - 목록은 사용자 본인(member) 기준으로만 조회됩니다(서버에서 인증 기반으로 식별).
      - 정렬 기준은 `publishedAt` 내림차순입니다.
      """
  )
  ResponseEntity<NotificationHistoryResponse> getNotificationHistoryPage(
    CustomUserDetails customUserDetails,
    NotificationHistoryRequest request
  );

  @ApiChangeLogs({
    @ApiChangeLog(date = "2026.01.28", author = Author.BAEKJIHOON, issueNumber = 441, description = "알림 히스토리 기능 추가")
  })
  @Operation(
    summary = "안읽은 알림 개수 조회",
    description = """
      ### 인증(JWT): **필요**
      
      ### 요청 파라미터
      - 없음
      
      ### 응답 데이터 (NotificationHistoryResponse)
      - `unReadCount` (Long): 안읽은 알림 개수
      
      ### 사용 방법
      1. 로그인(JWT) 후 API를 호출합니다.
      2. 응답의 `unReadCount` 값으로 미확인 알림 개수를 확인합니다.
      
      ### 유의 사항
      - 읽음 여부는 `isRead` 값 기준으로 집계됩니다.
      """
  )
  ResponseEntity<NotificationHistoryResponse> getUnReadNotificationCount(
    CustomUserDetails customUserDetails,
    NotificationHistoryRequest request
  );

  @ApiChangeLogs({
    @ApiChangeLog(date = "2026.01.28", author = Author.BAEKJIHOON, issueNumber = 441, description = "알림 히스토리 기능 추가")
  })
  @Operation(
    summary = "알림 읽음 처리",
    description = """
      ### 인증(JWT): **필요**
      
      ### 요청 파라미터 (NotificationHistoryRequest)
      - `notificationHistoryId` (UUID, required): 읽음 처리할 알림 히스토리 ID
      
      ### 반환값
      - 성공 시 상태코드 200 (OK)와 빈 응답 본문
      
      ### 사용 방법
      1. 로그인(JWT) 후, 읽음 처리할 알림의 `notificationHistoryId`를 전달하여 호출합니다.
      2. 서버는 해당 알림의 소유자(수신자)인지 검증 후 `isRead = true`로 변경합니다.
      
      ### 유의 사항
      - **알림의 수신자(소유자)만** 읽음 처리가 가능합니다.  
        (알림의 `memberId`와 요청 사용자 `memberId`가 다르면 예외 발생)
      
      ### 예외처리
      - `NOTIFICATION_HISTORY_NOT_FOUND` (400 BAD_REQUEST): 알림 히스토리를 찾을 수 없습니다
      - `INVALID_NOTIFICATION_HISTORY_OWNER` (400 BAD_REQUEST): 해당 알림의 수신자가 아닙니다
      """
  )
  ResponseEntity<Void> markAsRead(
    CustomUserDetails customUserDetails,
    NotificationHistoryRequest request
  );

  @ApiChangeLogs({
    @ApiChangeLog(date = "2026.01.28", author = Author.BAEKJIHOON, issueNumber = 441, description = "알림 히스토리 기능 추가")
  })
  @Operation(
    summary = "모든 알림 읽음 처리",
    description = """
      ### 인증(JWT): **필요**
      
      ### 요청 파라미터
      - 없음
      
      ### 반환값
      - 성공 시 상태코드 200 (OK)와 빈 응답 본문
      
      ### 사용 방법
      1. 로그인(JWT) 후 API를 호출합니다.
      2. 서버가 해당 사용자(memberId)의 알림을 대상으로 일괄 읽음 처리합니다.
      """
  )
  ResponseEntity<Void> markAllAsRead(
    CustomUserDetails customUserDetails,
    NotificationHistoryRequest request
  );

  @ApiChangeLogs({
    @ApiChangeLog(date = "2026.01.28", author = Author.BAEKJIHOON, issueNumber = 441, description = "알림 히스토리 기능 추가")
  })
  @Operation(
    summary = "알림 삭제",
    description = """
      ### 인증(JWT): **필요**
      
      ### 요청 파라미터 (NotificationHistoryRequest)
      - `notificationHistoryId` (UUID, required): 삭제할 알림 히스토리 ID
      
      ### 반환값
      - 성공 시 상태코드 200 (OK)와 빈 응답 본문
      
      ### 사용 방법
      1. 로그인(JWT) 후, 삭제할 알림의 `notificationHistoryId`를 전달하여 호출합니다.
      2. 서버는 해당 알림의 소유자(수신자)인지 검증 후 알림을 삭제합니다
      
      ### 유의 사항
      - **알림의 수신자(소유자)만** 삭제가 가능합니다.
      - 본 API는 **Hard Delete**로 동작합니다.
      
      ### 예외처리
      - `NOTIFICATION_HISTORY_NOT_FOUND` (400 BAD_REQUEST): 알림 히스토리를 찾을 수 없습니다
      - `INVALID_NOTIFICATION_HISTORY_OWNER` (400 BAD_REQUEST): 해당 알림의 수신자가 아닙니다
      """
  )
  ResponseEntity<Void> deleteNotification(
    CustomUserDetails customUserDetails,
    NotificationHistoryRequest request
  );

  @ApiChangeLogs({
    @ApiChangeLog(date = "2026.01.28", author = Author.BAEKJIHOON, issueNumber = 441, description = "알림 히스토리 기능 추가")
  })
  @Operation(
    summary = "전체 알림 삭제",
    description = """
      ### 인증(JWT): **필요**
      
      ### 요청 파라미터
      - 없음
      
      ### 반환값
      - 성공 시 상태코드 200 (OK)와 빈 응답 본문
      
      ### 사용 방법
      1. 로그인(JWT) 후 API를 호출합니다.
      2. 서버가 해당 사용자(member)의 알림을 삭제합니다
      """
  )
  ResponseEntity<Void> deleteAllNotifications(
    CustomUserDetails customUserDetails,
    NotificationHistoryRequest request
  );
}
