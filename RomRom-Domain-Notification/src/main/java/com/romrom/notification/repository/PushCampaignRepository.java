package com.romrom.notification.repository;

import com.romrom.common.constant.PushCampaignStatus;
import com.romrom.notification.entity.PushCampaign;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 푸시 캠페인 저장소: 상태 전환·집계는 조건부·원자적 UPDATE (다중 인스턴스 중복 실행 방지)
 */
public interface PushCampaignRepository extends JpaRepository<PushCampaign, UUID> {

  Page<PushCampaign> findAllByOrderByCreatedDateDesc(Pageable pageable);

  // 실행 시각 도래 예약 캠페인 (예약 시각순)
  @Query("SELECT p.pushCampaignId FROM PushCampaign p WHERE p.status = :status AND p.scheduledAt <= :now ORDER BY p.scheduledAt")
  List<UUID> findDueCampaignIds(@Param("status") PushCampaignStatus status, @Param("now") LocalDateTime now,
      Pageable pageable);

  // 선점: SCHEDULED > SENDING (1 = 선점 성공, 0 = 다른 인스턴스 선점·취소)
  @Modifying
  @Transactional
  @Query("""
      UPDATE PushCampaign p SET p.status = com.romrom.common.constant.PushCampaignStatus.SENDING,
        p.startedAt = :now, p.updatedDate = :now
      WHERE p.pushCampaignId = :pushCampaignId AND p.status = com.romrom.common.constant.PushCampaignStatus.SCHEDULED
      """)
  int claimScheduledCampaign(@Param("pushCampaignId") UUID pushCampaignId, @Param("now") LocalDateTime now);

  // 선점 반납: 실행기 거절 시 SENDING > SCHEDULED (다음 주기 재시도)
  @Modifying
  @Transactional
  @Query("""
      UPDATE PushCampaign p SET p.status = com.romrom.common.constant.PushCampaignStatus.SCHEDULED,
        p.startedAt = null, p.updatedDate = :now
      WHERE p.pushCampaignId = :pushCampaignId AND p.status = com.romrom.common.constant.PushCampaignStatus.SENDING
      """)
  int releaseClaimedCampaign(@Param("pushCampaignId") UUID pushCampaignId, @Param("now") LocalDateTime now);

  // 예약 취소: SCHEDULED 일 때만
  @Modifying
  @Transactional
  @Query("""
      UPDATE PushCampaign p SET p.status = com.romrom.common.constant.PushCampaignStatus.CANCELED,
        p.completedAt = :now, p.updatedDate = :now
      WHERE p.pushCampaignId = :pushCampaignId AND p.status = com.romrom.common.constant.PushCampaignStatus.SCHEDULED
      """)
  int cancelScheduledCampaign(@Param("pushCampaignId") UUID pushCampaignId, @Param("now") LocalDateTime now);

  // 종료: SENDING > COMPLETED·FAILED
  @Modifying
  @Transactional
  @Query("""
      UPDATE PushCampaign p SET p.status = :finalStatus, p.completedAt = :now, p.failureReason = :failureReason,
        p.updatedDate = :now
      WHERE p.pushCampaignId = :pushCampaignId AND p.status = com.romrom.common.constant.PushCampaignStatus.SENDING
      """)
  int finishSendingCampaign(@Param("pushCampaignId") UUID pushCampaignId,
      @Param("finalStatus") PushCampaignStatus finalStatus, @Param("failureReason") String failureReason,
      @Param("now") LocalDateTime now);

  // 중단된 발송 정리: 시작 후 기준 시각 지난 SENDING > FAILED (재발송 없음)
  @Modifying
  @Transactional
  @Query("""
      UPDATE PushCampaign p SET p.status = com.romrom.common.constant.PushCampaignStatus.FAILED,
        p.completedAt = :now, p.failureReason = :failureReason, p.updatedDate = :now
      WHERE p.status = com.romrom.common.constant.PushCampaignStatus.SENDING AND p.startedAt < :startedBefore
      """)
  int failStaleSendingCampaigns(@Param("startedBefore") LocalDateTime startedBefore,
      @Param("failureReason") String failureReason, @Param("now") LocalDateTime now);

  // 페이지 발송 집계 누적 (원자적 증가)
  @Modifying
  @Transactional
  @Query("""
      UPDATE PushCampaign p SET p.targetMemberCount = p.targetMemberCount + :targetMemberCount,
        p.sentCount = p.sentCount + :sentCount, p.failureCount = p.failureCount + :failureCount,
        p.removedTokenCount = p.removedTokenCount + :removedTokenCount
      WHERE p.pushCampaignId = :pushCampaignId
      """)
  int addSendCounts(@Param("pushCampaignId") UUID pushCampaignId, @Param("targetMemberCount") int targetMemberCount,
      @Param("sentCount") int sentCount, @Param("failureCount") int failureCount,
      @Param("removedTokenCount") int removedTokenCount);

  // 열람 수 1 증가 (원자적 증가)
  @Modifying
  @Transactional
  @Query("UPDATE PushCampaign p SET p.openedCount = p.openedCount + 1 WHERE p.pushCampaignId = :pushCampaignId")
  int incrementOpenedCount(@Param("pushCampaignId") UUID pushCampaignId);
}
