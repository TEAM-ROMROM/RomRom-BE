package com.romrom.notification.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.constant.DeviceType;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.member.entity.Member;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import lombok.AllArgsConstructor;
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
@ToString
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class FcmToken extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID fcmTokenId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member member;

  private String token;

  private DeviceType deviceType;
}