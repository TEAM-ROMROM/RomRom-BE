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
import jakarta.persistence.OneToOne;
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

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID notificationHistoryId;

  @OneToOne(fetch = FetchType.LAZY)
  private Member member;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationType notificationType;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String body;

  @Column(nullable = false)
  @Convert(converter = NotificationPayloadConverter.class)
  private Map<String, String> payload;

  @Builder.Default
  @Column(nullable = false)
  private Boolean isRead = false;

  @Column(nullable = false)
  private LocalDateTime publishedAt;
}
