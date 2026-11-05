package com.romrom.application.service;

import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.common.constant.SanctionType;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.member.repository.mongo.SanctionHistoryRepository;
import com.romrom.notification.entity.PushCampaign;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 관리자 > 회원 단건 알림 발송: 대상 검증 > 회원 지정(MEMBERS) 푸시 캠페인 즉시 발송 > 감사 이력 (#874)
 * 트랜잭션 없음: 캠페인 저장이 커밋된 뒤 실행기가 읽어야 함
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMemberNotificationService {

  private final SanctionHistoryRepository sanctionHistoryRepository;
  private final AdminMemberSanctionService adminMemberSanctionService;
  private final AdminPushCampaignService adminPushCampaignService;

  /**
   * 회원에게 알림 발송 (관리자 → 회원 단건): 목적 미입력 > 정보성, 딥링크 선택
   * 알림 종류: 정보성 ANNOUNCEMENT, 광고성 MARKETING (notificationTypeName 은 옛 요청 호환용으로 무시)
   */
  public PushCampaign sendNotificationToMember(UUID targetMemberId, String title, String content,
      PushCampaignPurpose purpose, String deepLink, UUID executorAdminId) {
    adminMemberSanctionService.validateAdminAction(targetMemberId, executorAdminId);

    PushCampaign memberCampaign = adminPushCampaignService.sendMemberCampaign(targetMemberId, title, content,
        purpose == null ? PushCampaignPurpose.INFORMATION : purpose, deepLink, executorAdminId);

    // 감사 로그
    SanctionHistory notificationAuditSanction = SanctionHistory.builder()
        .memberId(targetMemberId)
        .suspendReason("[ADMIN_NOTIFICATION] title=" + memberCampaign.getTitle())
        .suspendedAt(LocalDateTime.now(ZoneOffset.UTC))
        .sanctionType(SanctionType.ADMIN_NOTIFICATION)
        .executorAdminId(executorAdminId)
        .build();
    sanctionHistoryRepository.save(notificationAuditSanction);

    log.info("[admin member notification] sent : targetMemberId={}, executorAdminId={}, pushCampaignId={}",
        targetMemberId, executorAdminId, memberCampaign.getPushCampaignId());
    return memberCampaign;
  }
}
