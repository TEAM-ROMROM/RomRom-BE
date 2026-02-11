package com.romrom.member.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.Role;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
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
public class Member extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID memberId;

  @Column(unique = true)
  private String email;

  @Column(unique = true)
  private String nickname;

  // 소셜 플랫폼 (KAKAO, GOOGLE)
  @Enumerated(EnumType.STRING)
  private SocialPlatform socialPlatform;

  // 프로필 이미지 URL
  private String profileUrl;

  @Enumerated(EnumType.STRING)
  private Role role;

  @Enumerated(EnumType.STRING)
  private AccountStatus accountStatus;

  private LocalDateTime lastActiveAt;  // 마지막 활동 시간

  @Transient
  private boolean isOnline;            // 온라인 상태 여부 (서버 저장 X)

  // 첫 로그인 여부
  @Column(nullable = false)
  @Builder.Default
  private Boolean isFirstLogin = true;

  // 선호 카테고리 설정 여부
  @Column(nullable = false)
  @Builder.Default
  private Boolean isItemCategorySaved = false;

  // 물품 등록 여부
  @Column(nullable = false)
  @Builder.Default
  private Boolean isFirstItemPosted = false;

  // 위치정보 저장 여부
  @Column(nullable = false)
  @Builder.Default
  private Boolean isMemberLocationSaved = false;

  // 필수 약관 동의 여부
  @Column(nullable = false)
  @Builder.Default
  private Boolean isRequiredTermsAgreed = false;

  // 마케팅 약관 동의 여부
  @Column(nullable = false)
  @Builder.Default
  private Boolean isMarketingInfoAgreed = false;

  // 활동 알림 동의 여부
  @Column(nullable = false)
  @Builder.Default
  private Boolean isActivityNotificationAgreed = false;

  // 채팅 알림 동의 여부
  @Column(nullable = false)
  @Builder.Default
  private Boolean isChatNotificationAgreed = false;

  // 콘텐츠 알림 동의 여부
  @Column(nullable = false)
  @Builder.Default
  private Boolean isContentNotificationAgreed = false;

  // 거래 알림 동의 여부
  @Column(nullable = false)
  @Builder.Default
  private Boolean isTradeNotificationAgreed = false;

  // 관리자 계정용 암호화된 비밀번호 (Admin 계정만 사용)
  @JsonIgnore
  private String password;

  // 회원 삭제 여부
  @Column(nullable = false)
  @Builder.Default
  @JsonIgnore
  private Boolean isDeleted = false;

  @Transient
  private Double latitude;   // 위도 (Y)

  @Transient
  private Double longitude;  // 경도 (X)

  @Column(nullable = false)
  @Builder.Default
  private Integer totalLikeCount = 0; // 받은 좋아요 수

  // 탐색 범위
  private Double searchRadiusInMeters;

  @Transient
  private Boolean isBlocked;

  @Transient
  private String locationAddress;  // "서울특별시 광진구 화양동" 형식

  public void increaseTotalLikeCount() {
    totalLikeCount++;
  }

  public void decreaseTotalLikeCount() {
    totalLikeCount--;
    if (totalLikeCount < 0) {
      totalLikeCount = 0;
    }
  }

  //@PostLoad // 엔티티가 영속성 컨텍스트 로드된 후 호출되는 콜백 메서드. 객체가 로드된 시점의 시간인 점을 주의.
  public void setOnlineIfActiveWithin90Seconds() {
    if (lastActiveAt == null) {
      this.isOnline = false;
      return;
    }
    LocalDateTime thresholdTime = LocalDateTime.now().minusSeconds(90);
    this.isOnline = lastActiveAt.isAfter(thresholdTime);
  }
}
