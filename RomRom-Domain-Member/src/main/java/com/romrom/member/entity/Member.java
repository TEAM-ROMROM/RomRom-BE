package com.romrom.member.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.Role;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
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

  // 탐색 범위 미설정 회원 기본값 (홈 피드·키워드 알림 공통, 10km)
  public static final double DEFAULT_SEARCH_RADIUS_IN_METERS = 10000.0;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID memberId;

  @Column(unique = true)
  private String email;

  // 카카오 Custom Token 방식 로그인 시 Firebase UID (kakao:{카카오회원번호} 형식)
  @Column(unique = true)
  private String firebaseUid;

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
  private Boolean isOnline;            // 온라인 상태 여부 (서버 저장 X)

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

  // 마케팅 수신 동의 값이 바뀐 시각 (#874, 2년 주기 동의 상태 안내 기준)
  private LocalDateTime marketingConsentChangedAt;

  // 마케팅 수신 동의 상태 안내 시각 (#874, 다음 안내는 2년 뒤)
  private LocalDateTime marketingConsentRemindedAt;

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

  // 방해금지 시간 (#861): 사용 중이면 시작~종료(KST) 동안 푸시 생략, 알림함 기록은 유지
  @Column(nullable = false)
  @Builder.Default
  private Boolean isDoNotDisturbEnabled = false;

  // 방해금지 시작 시각 (KST, 응답 "HH:mm")
  @Column(nullable = false)
  @Builder.Default
  @JsonFormat(pattern = "HH:mm")
  private LocalTime doNotDisturbStartTime = LocalTime.of(22, 0);

  // 방해금지 종료 시각 (KST, 시작보다 이르면 다음 날)
  @Column(nullable = false)
  @Builder.Default
  @JsonFormat(pattern = "HH:mm")
  private LocalTime doNotDisturbEndTime = LocalTime.of(8, 0);

  // 관리자 계정용 암호화된 비밀번호 (Admin 계정만 사용)
  @JsonIgnore
  private String password;

  // 회원 삭제 여부
  @Column(nullable = false)
  @Builder.Default
  @JsonIgnore
  private Boolean isDeleted = false;

  @Column(name = "suspend_reason")
  private String suspendReason;

  @Column(name = "suspended_at")
  private LocalDateTime suspendedAt;

  @Column(name = "suspended_until")
  private LocalDateTime suspendedUntil;

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

  /**
   * 마케팅 수신 동의 반영: 값 변경 또는 첫 기록 > 변경 시각 기록·안내 시각 초기화 (null > 유지)
   */
  public void changeMarketingConsent(Boolean isAgreed, LocalDateTime changedAt) {
    if (isAgreed == null) {
      return;
    }
    boolean isConsentValueChanged = !isAgreed.equals(isMarketingInfoAgreed);
    if (!isConsentValueChanged && marketingConsentChangedAt != null) {
      return;
    }
    this.isMarketingInfoAgreed = isAgreed;
    this.marketingConsentChangedAt = changedAt;
    this.marketingConsentRemindedAt = null;
  }

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
