package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.notification.dto.AnnouncementRequest;
import com.romrom.notification.dto.AnnouncementResponse;
import com.romrom.notification.dto.NotificationHistoryRequest;
import com.romrom.notification.dto.NotificationHistoryResponse;
import com.romrom.notification.dto.NotificationRequest;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface NotificationControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "토큰 값 기준 저장: 갱신 토큰 저장, 회원당 여러 기기, 같은 토큰 다른 계정 로그인 시 회원 이동"),
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
      - 토큰 값 기준으로 저장합니다: 같은 토큰이면 회원·기기 종류·갱신일만 갱신, 새 토큰이면 새로 저장 (회원 1명 여러 기기 가능)
      - 같은 토큰이 다른 계정으로 들어오면 그 계정으로 옮깁니다 (기기에서 계정 전환)
      - 앱 시작·토큰 갱신마다 다시 보내 주세요. 갱신일이 90일 지난 토큰은 매일 정리됩니다
      - 로그아웃 시 `/api/auth/logout`에 `fcmToken`을 보내면 그 토큰이 삭제됩니다
      """
  )
  ResponseEntity<Void> saveFcmToken(CustomUserDetails customUserDetails, NotificationRequest request);

  @ApiChangeLogs({
    @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "확장 알림 타입 MARKETING(광고성 푸시 캠페인) 추가, 캠페인 알림 payload campaignId"),
    @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 873, description = "확장 알림 타입 KEYWORD_ITEM_REGISTERED(키워드 알림) 추가"),
    @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 861, description = "확장 알림 타입 4종 추가, isExtendedTypeSupported=true일 때만 포함"),
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
      - `isExtendedTypeSupported` (Boolean, optional): 확장 알림 타입 표시 가능 앱 여부
        - 기본값: `false` (미전달 = 옛 앱 > 확장 타입 4종 제외)
      
      ### 응답 데이터 (NotificationHistoryResponse)
      - `notificationHistoryPage` (Page<NotificationHistory>): 알림 히스토리 페이지 데이터
        - 정렬: `publishedAt`기준 **내림차순**
      
      ### NotificationHistory 필드
      - `notificationHistoryId` (UUID): 알림 히스토리 ID
      - `notificationType` (NotificationType): 알림 타입
        - 가능한 값: `TRADE_REQUEST_RECEIVED`, `CHAT_MESSAGE_RECEIVED`, `ITEM_LIKED`, `ANNOUNCEMENT`, `ITEM_DELETED_BY_ADMIN`
        - 확장 타입 (`isExtendedTypeSupported=true`일 때만): `TRADE_REQUEST_ACCEPTED`(요청 수락, 채팅방 딥링크), `TRADE_REQUEST_REJECTED`(요청 거절, 물품 상세 딥링크), `TRADE_COMPLETED`(교환 완료), `TRADE_REVIEW_RECEIVED`(후기 도착, `romrom://profile/reviews`), `KEYWORD_ITEM_REGISTERED`(키워드 알림, 물품 상세 딥링크, payload `itemId`·`keyword`), `MARKETING`(이벤트·혜택 광고성 푸시, 제목 "(광고) ", payload `deepLink`·`campaignId`)
        - 관리자 푸시 캠페인·공지 푸시 기록: `ANNOUNCEMENT`/`MARKETING` + payload `campaignId`, 선택 `deepLink`(공지·물품이면 `announcementId`·`itemId` 함께)
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
      - 옛 앱(v1.10.142 이하)은 모르는 타입을 공지로 표시하고 끄기 버튼이 콘텐츠·마케팅 동의를 끄므로 확장 타입을 숨깁니다.
      - 방해금지 시간 중 받은 알림도 목록에는 저장됩니다(푸시만 생략).
      """
  )
  ResponseEntity<NotificationHistoryResponse> getNotificationHistoryPage(
    CustomUserDetails customUserDetails,
    NotificationHistoryRequest request
  );

  @ApiChangeLogs({
    @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 861, description = "isExtendedTypeSupported 미전달 시 확장 알림 타입 제외 집계"),
    @ApiChangeLog(date = "2026.01.28", author = Author.BAEKJIHOON, issueNumber = 441, description = "알림 히스토리 기능 추가")
  })
  @Operation(
    summary = "안읽은 알림 개수 조회",
    description = """
      ### 인증(JWT): **필요**
      
      ### 요청 파라미터
      - `isExtendedTypeSupported` (Boolean, optional): 기본값 `false` > 목록과 같은 기준으로 확장 알림 타입 4종 제외
      
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

  @ApiChangeLogs({
    @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 861, description = "사용자 공지 상세 조회 API 추가")
  })
  @Operation(
    summary = "공지 상세 조회",
    description = """
      ### 인증(JWT): **필요**
      
      ### 요청 파라미터 (AnnouncementRequest, multipart/form-data)
      - `announcementId` (UUID, required): 공지사항 ID (공지 푸시 payload `announcementId`, 딥링크 `romrom://notice/detail?announcementId=`)
      
      ### 응답 데이터 (AnnouncementResponse)
      - `announcement` (Announcement)
        - `announcementId` (UUID), `title` (String), `content` (String)
        - `imageUrl` (String, nullable), `linkUrl` (String, nullable)
        - `createdDate` (LocalDateTime): 작성 시각
      
      ### 예외처리
      - `INVALID_REQUEST` (400): announcementId 누락
      - `ANNOUNCEMENT_NOT_FOUND` (404): 없는(삭제된) 공지
      """
  )
  ResponseEntity<AnnouncementResponse> getAnnouncementDetail(
    CustomUserDetails customUserDetails,
    AnnouncementRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "푸시 열람 기록 API 추가"),
  })
  @Operation(
      summary = "푸시 열람 기록",
      description = """
      ## 인증(JWT): **필요**

      ## 요청 파라미터 (NotificationRequest, multipart/form-data)
      - **`campaignId`**: 연 푸시의 FCM data `campaignId`

      ## 반환값
      - 항상 상태코드 200 (OK)와 빈 응답 본문

      ## 동작 설명
      - 같은 회원이 같은 캠페인을 여러 번 열어도 1회만 셉니다 (관리자 캠페인 열람 수)
      - `campaignId` 누락·형식 오류·없는 캠페인이면 기록 없이 200 (앱은 실패를 무시해도 됨)
      - 앱 시작 경로·백그라운드·포그라운드 탭 모두에서 호출해도 됩니다
      """
  )
  ResponseEntity<Void> recordPushOpen(CustomUserDetails customUserDetails, NotificationRequest request);
}
