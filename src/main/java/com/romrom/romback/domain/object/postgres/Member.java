package com.romrom.romback.domain.object.postgres;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.romback.domain.object.constant.AccountStatus;
import com.romrom.romback.domain.object.constant.Role;
import com.romrom.romback.domain.object.constant.SocialPlatform;
import com.romrom.romback.global.util.BasePostgresEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

  // 회원 삭제 여부
  @Column(nullable = false)
  @Builder.Default
  @JsonIgnore
  private Boolean isDeleted = false;
}
