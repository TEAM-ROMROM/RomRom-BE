package com.romrom.notification.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 푸시 열람 기록 (#874): 회원·캠페인당 1행 (unique), 열람 시각 = createdDate
 * 테이블 원본: Flyway V1_4_76
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "push_campaign_open",
    uniqueConstraints = @UniqueConstraint(name = "uk_push_campaign_open_campaign_member",
        columnNames = {"push_campaign_id", "member_id"}))
public class PushCampaignOpen extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID pushCampaignOpenId;

  @Column(nullable = false)
  private UUID pushCampaignId;

  @Column(nullable = false)
  private UUID memberId;
}
