package com.romrom.notification.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.converter.NotificationPayloadConverter;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.member.entity.Member;
import com.romrom.notification.event.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class NotificationHistory extends BasePostgresEntity {

  // 본문 컬럼 길이 (Flyway V1_4_76 과 함께 변경)
  public static final int BODY_COLUMN_LENGTH = 2000;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID notificationHistoryId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member member;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationType notificationType;

  @Column(nullable = false)
  private String title;

  // 본문 (캠페인 본문 최대 1000자 + 광고 수신거부 안내, #874)
  @Column(nullable = false, length = BODY_COLUMN_LENGTH)
  private String body;

  @Column(nullable = false, columnDefinition = "TEXT")
  @Convert(converter = NotificationPayloadConverter.class)
  private Map<String, String> payload;

  @Builder.Default
  @Column(nullable = false)
  private Boolean isRead = false;

  @Column(nullable = false)
  private LocalDateTime publishedAt;
}
