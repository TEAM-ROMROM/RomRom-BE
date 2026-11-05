package com.romrom.notification.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.common.constant.PushCampaignStatus;
import com.romrom.common.constant.PushCampaignTargetType;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 푸시 캠페인 (#874): 내용·목적·대상·상태·예약 시각·발송 집계
 * 테이블 원본: Flyway V1_4_76, 집계 컬럼은 원자적 증가 쿼리로만 갱신
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "push_campaign", indexes = {
    @Index(name = "idx_push_campaign_status_scheduled_at", columnList = "status, scheduled_at"),
    @Index(name = "idx_push_campaign_created_date", columnList = "created_date")
})
public class PushCampaign extends BasePostgresEntity {

  public static final int TITLE_MAXIMUM_LENGTH = 100;
  public static final int BODY_MAXIMUM_LENGTH = 1000;
  public static final int LINK_MAXIMUM_LENGTH = 1000;
  public static final int FAILURE_REASON_MAXIMUM_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID pushCampaignId;

  // 작성 원문 (광고 표시 문구 제외, 발송 시 AdvertisementPushRule 로 부착)
  @Column(nullable = false, length = TITLE_MAXIMUM_LENGTH)
  private String title;

  @Column(nullable = false, length = BODY_MAXIMUM_LENGTH)
  private String body;

  // 알림 이미지 (https://, 선택)
  @Column(length = LINK_MAXIMUM_LENGTH)
  private String imageUrl;

  // 알림 탭 이동 경로 (romrom:// 또는 https://, 선택)
  @Column(length = LINK_MAXIMUM_LENGTH)
  private String deepLink;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PushCampaignPurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PushCampaignTargetType targetType;

  // 대상 조건 JSON (PushCampaignTargetFilter 저장 형태)
  @Column(columnDefinition = "TEXT")
  private String targetFilter;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private PushCampaignStatus status;

  // 실행 예정 시각 (즉시 발송 > 생성 시각, KST)
  @Column(nullable = false)
  private LocalDateTime scheduledAt;

  private LocalDateTime startedAt;

  private LocalDateTime completedAt;

  // 대상 회원 수 (알림함 기록 대상, 페이지마다 누적)
  @Builder.Default
  @Column(nullable = false)
  private Integer targetMemberCount = 0;

  // 발송 성공 토큰 수
  @Builder.Default
  @Column(nullable = false)
  private Integer sentCount = 0;

  // 발송 실패 토큰 수
  @Builder.Default
  @Column(nullable = false)
  private Integer failureCount = 0;

  // 무효로 삭제한 토큰 수
  @Builder.Default
  @Column(nullable = false)
  private Integer removedTokenCount = 0;

  // 푸시 열람 회원 수 (회원·캠페인당 1회)
  @Builder.Default
  @Column(nullable = false)
  private Integer openedCount = 0;

  // 작성 관리자 (공지 자동 발송 > null)
  private UUID createdByAdminId;

  @Column(length = FAILURE_REASON_MAXIMUM_LENGTH)
  private String failureReason;
}
