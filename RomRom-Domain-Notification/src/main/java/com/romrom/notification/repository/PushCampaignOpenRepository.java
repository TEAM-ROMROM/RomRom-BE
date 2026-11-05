package com.romrom.notification.repository;

import com.romrom.notification.entity.PushCampaignOpen;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PushCampaignOpenRepository extends JpaRepository<PushCampaignOpen, UUID> {

  // 열람 기록 1회: 이미 있으면 무시 (1 = 새 기록, 0 = 중복)
  @Modifying
  @Transactional
  @Query(value = """
      INSERT INTO push_campaign_open (push_campaign_open_id, push_campaign_id, member_id, created_date, updated_date)
      VALUES (:pushCampaignOpenId, :pushCampaignId, :memberId, :now, :now)
      ON CONFLICT (push_campaign_id, member_id) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(@Param("pushCampaignOpenId") UUID pushCampaignOpenId,
      @Param("pushCampaignId") UUID pushCampaignId, @Param("memberId") UUID memberId,
      @Param("now") LocalDateTime now);
}
