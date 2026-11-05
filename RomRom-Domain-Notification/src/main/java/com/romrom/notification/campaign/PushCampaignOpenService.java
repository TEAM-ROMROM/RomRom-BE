package com.romrom.notification.campaign;

import com.romrom.notification.repository.PushCampaignOpenRepository;
import com.romrom.notification.repository.PushCampaignRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 푸시 열람 기록: 캠페인 ID 확인 > 회원·캠페인 1회 기록(중복 무시) > 새 기록일 때만 열람 수 원자적 +1
 * 형식 오류·없는 캠페인·DB 오류 > 기록 없이 종료 (앱 통계 호출이라 오류 응답 없음)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushCampaignOpenService {

  private final PushCampaignRepository pushCampaignRepository;
  private final PushCampaignOpenRepository pushCampaignOpenRepository;

  /**
   * 열람 기록 > 새로 센 경우 true
   */
  public boolean recordOpen(UUID memberId, String campaignIdText) {
    UUID pushCampaignId = parseCampaignIdOrNull(campaignIdText);
    if (memberId == null || pushCampaignId == null) {
      log.debug("[push open] ignored : invalid campaignId");
      return false;
    }
    try {
      if (!pushCampaignRepository.existsById(pushCampaignId)) {
        log.debug("[push open] ignored : campaign not found, pushCampaignId={}", pushCampaignId);
        return false;
      }
      int insertedOpenCount = pushCampaignOpenRepository.insertIfAbsent(UUID.randomUUID(), pushCampaignId, memberId,
          LocalDateTime.now());
      if (insertedOpenCount == 0) {
        return false;
      }
      pushCampaignRepository.incrementOpenedCount(pushCampaignId);
      return true;
    } catch (DataAccessException openRecordException) {
      log.warn("[push open] failed : pushCampaignId={}, error={}", pushCampaignId, openRecordException.getMessage());
      return false;
    }
  }

  private static UUID parseCampaignIdOrNull(String campaignIdText) {
    if (!StringUtils.hasText(campaignIdText)) {
      return null;
    }
    try {
      return UUID.fromString(campaignIdText.trim());
    } catch (IllegalArgumentException invalidCampaignIdException) {
      return null;
    }
  }
}
