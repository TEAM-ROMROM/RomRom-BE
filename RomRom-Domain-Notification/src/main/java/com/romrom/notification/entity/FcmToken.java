package com.romrom.notification.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.constant.DeviceType;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.member.entity.Member;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
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
 * FCM 토큰: 토큰 값 1개당 1행 (회원 1명 여러 기기, 같은 기기 계정 전환 시 회원 이동, #874)
 * 제약 원본: Flyway V1_4_76 (uk_fcm_token_token, idx_fcm_token_updated_date)
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"member", "token"})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "fcm_token",
    uniqueConstraints = @UniqueConstraint(name = "uk_fcm_token_token", columnNames = "token"),
    indexes = @Index(name = "idx_fcm_token_updated_date", columnList = "updated_date"))
public class FcmToken extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID fcmTokenId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member member;

  private String token;

  // smallint 순번 저장 (ANDROID 0, IOS 1, OTHER 2): 일괄 발송 조회 SQL이 같은 값 사용
  @Enumerated(EnumType.ORDINAL)
  private DeviceType deviceType;
}
