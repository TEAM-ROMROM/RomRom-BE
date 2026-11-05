package com.romrom.notification.campaign;

import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.common.constant.PushCampaignStatus;
import com.romrom.common.constant.PushCampaignTargetType;
import com.romrom.notification.entity.PushCampaign;
import com.romrom.notification.event.AnnouncementEvent;
import com.romrom.notification.repository.PushCampaignRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 공지 푸시 (#874): 정보성·전체 대상 캠페인 1건 생성 > 즉시 실행
 * 콘텐츠 알림 동의자에게만 푸시, 알림함 기록은 활성 회원 전원 (캠페인 목록에서 집계·열람 확인)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementPushService {

  private final PushCampaignRepository pushCampaignRepository;
  private final PushCampaignDispatcher pushCampaignDispatcher;

  public void sendAnnouncementPush(AnnouncementEvent announcementEvent) {
    LocalDateTime now = LocalDateTime.now();
    PushCampaign announcementCampaign = pushCampaignRepository.save(PushCampaign.builder()
        .title(announcementEvent.getTitle())
        .body(announcementEvent.getBody())
        .deepLink(PushCampaignPayloadFactory.ANNOUNCEMENT_DETAIL_DEEP_LINK_PREFIX + announcementEvent.getAnnouncementId())
        .purpose(PushCampaignPurpose.INFORMATION)
        .targetType(PushCampaignTargetType.ALL)
        .targetFilter(PushCampaignTargetFilter.empty().toJson())
        .status(PushCampaignStatus.SCHEDULED)
        .scheduledAt(now)
        .build());
    boolean isDispatched = pushCampaignDispatcher.claimAndDispatch(announcementCampaign.getPushCampaignId());
    log.info("[push campaign] announcement push created : announcementId={}, pushCampaignId={}, dispatched={}",
        announcementEvent.getAnnouncementId(), announcementCampaign.getPushCampaignId(), isDispatched);
  }
}
