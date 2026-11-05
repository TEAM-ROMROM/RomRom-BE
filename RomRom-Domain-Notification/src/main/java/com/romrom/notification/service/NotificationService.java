package com.romrom.notification.service;

import com.romrom.member.entity.Member;
import com.romrom.member.service.MemberService;
import com.romrom.notification.dto.NotificationHistoryRequest;
import com.romrom.notification.entity.FcmToken;
import com.romrom.notification.event.NotificationType;
import com.romrom.notification.push.FcmBatchSender;
import com.romrom.notification.push.FcmPushContent;
import com.romrom.notification.push.FcmPushTarget;
import com.romrom.notification.repository.FcmTokenRepository;
import com.romrom.notification.repository.NotificationHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 이벤트 알림 발송 (회원 1명·여러 명): 알림함 기록 > 수신 동의 > 방해금지 > 회원 토큰 일괄 발송(iOS 배지 = 안 읽은 수)
 * 대량 발송(공지·캠페인)은 PushCampaignSendService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

  // 전체 테스트 발송 토큰 페이지 크기
  private static final int ALL_TOKEN_PAGE_SIZE = 1000;

  private final FcmTokenService fcmTokenService;
  private final NotificationHistoryService notificationHistoryService;
  private final MemberService memberService;
  private final FcmBatchSender fcmBatchSender;
  private final FcmTokenRepository fcmTokenRepository;
  private final NotificationHistoryRepository notificationHistoryRepository;

  /**
   * 단일 사용자 알림 전송
   */
  public void sendToMember(UUID memberId, String title, String body, Map<String, String> payload) {
    // 알림 히스토리 저장
    NotificationType notificationType = NotificationType.valueOf(payload.get("notificationType"));
    LocalDateTime publishedAt = LocalDateTime.parse(payload.get("publishedAt"));
    Member member = memberService.findMemberById(memberId);

    // 채팅 알림은 히스토리에 저장하지 않음
    if (notificationType != NotificationType.CHAT_MESSAGE_RECEIVED) {
      try {
        notificationHistoryService.saveNotificationHistory(
          NotificationHistoryRequest.builder()
            .member(member)
            .notificationType(notificationType)
            .title(title)
            .body(body)
            .payload(payload)
            .publishedAt(publishedAt)
            .build()
        );
      } catch (Exception e) {
        log.error("알림 히스토리 저장 실패: memberId={}, title={}", memberId, title, e);
        // 히스토리 저장 실패 시에도 FCM 알림 발송 진행
      }
    }

    // 푸시 알림 설정 체크: 해당 카테고리의 알림이 비활성화 상태면 FCM 발송 스킵
    if (!isNotificationTypeAgreed(member, notificationType)) {
      log.debug("푸시 알림 비활성화 상태로 FCM 발송 스킵: memberId={}, type={}",
        memberId, notificationType);
      return;
    }

    // 방해금지 시간 중이면 FCM 발송 스킵 (알림함 기록은 위에서 저장, #861)
    if (DoNotDisturbTimeRule.isDoNotDisturbNow(member)) {
      log.debug("[notification do not disturb] push skipped : memberId={}, type={}", memberId, notificationType);
      return;
    }

    // FCM 알림 전송: 회원 전체 기기, iOS 배지 = 안 읽은 알림 수 (#874)
    List<FcmPushTarget> pushTargets = fcmTokenService.findAllTokensByMemberId(memberId).stream()
        .map(fcmToken -> new FcmPushTarget(fcmToken.getToken(), memberId, fcmToken.getDeviceType()))
        .toList();
    if (pushTargets.isEmpty()) {
      return;
    }
    int unreadNotificationCount = (int) notificationHistoryRepository.countByMemberAndIsRead(member, false);
    fcmBatchSender.send(pushTargets, new FcmPushContent(title, body, null, payload,
        FcmPushContent.DEFAULT_TIME_TO_LIVE, unreadNotificationCount));
  }

  /**
   * 사용자 리스트에게 알림 전송 (단일 or 다수)
   */
  public void sendToMembers(List<UUID> memberIds, String title, String body, Map<String, String> payload) { // TODO: 추후 알림 도메인 구성 후 파라미터 수정
    memberIds.forEach(memberId -> {
      try {
        sendToMember(memberId, title, body, payload);
      } catch (Exception e) {
        log.error("알림 전송 실패 (memberId={}), {}", memberId, e.getMessage(), e);
      }
    });
  }

  /**
   * 전체 토큰 테스트 발송 (테스트 API 전용, 토큰 1000개씩 페이지 조회 > 500건 묶음 발송)
   */
  public void sendToAll(String title, String body, Map<String, String> payload) {
    FcmPushContent pushContent = new FcmPushContent(title, body, null, payload, FcmPushContent.DEFAULT_TIME_TO_LIVE, null);
    int pageNumber = 0;
    Page<FcmToken> fcmTokenPage;
    do {
      fcmTokenPage = fcmTokenRepository.findAll(PageRequest.of(pageNumber++, ALL_TOKEN_PAGE_SIZE));
      List<FcmPushTarget> pushTargets = fcmTokenPage.getContent().stream()
          .map(fcmToken -> new FcmPushTarget(fcmToken.getToken(), null, fcmToken.getDeviceType()))
          .toList();
      fcmBatchSender.send(pushTargets, pushContent);
    } while (fcmTokenPage.hasNext());
  }

  /**
   * NotificationType 별 알림 동의 여부 확인
   */
  private boolean isNotificationTypeAgreed(Member member, NotificationType notificationType) {
    switch (notificationType) {
      case ITEM_LIKED -> {
        return Boolean.TRUE.equals(member.getIsActivityNotificationAgreed());
      }
      case TRADE_REQUEST_RECEIVED -> {
        return Boolean.TRUE.equals(member.getIsTradeNotificationAgreed());
      }
      case CHAT_MESSAGE_RECEIVED -> {
        return Boolean.TRUE.equals(member.getIsChatNotificationAgreed());
      }
      case ANNOUNCEMENT -> {
        return Boolean.TRUE.equals(member.getIsContentNotificationAgreed());
      }
      // 거래 소식 확장 (#861): 수락·거절·완료 > 교환 알림, 후기 > 활동 알림
      case TRADE_REQUEST_ACCEPTED, TRADE_REQUEST_REJECTED, TRADE_COMPLETED -> {
        return Boolean.TRUE.equals(member.getIsTradeNotificationAgreed());
      }
      case TRADE_REVIEW_RECEIVED -> {
        return Boolean.TRUE.equals(member.getIsActivityNotificationAgreed());
      }
      // 키워드 알림 (#873): 키워드 등록 자체가 수신 동의 (방해금지 시간은 적용)
      case KEYWORD_ITEM_REGISTERED -> {
        return true;
      }
      // 광고성 (#874): 마케팅 정보 수신 동의
      case MARKETING -> {
        return Boolean.TRUE.equals(member.getIsMarketingInfoAgreed());
      }
    }
    return true;
  }
}
