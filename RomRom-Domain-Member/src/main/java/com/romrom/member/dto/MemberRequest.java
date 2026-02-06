package com.romrom.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.romrom.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString(exclude = "member")
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class MemberRequest {

  @Schema(hidden = true, description = "회원")
  @JsonIgnore
  private Member member;

  @Schema(description = "차단 대상 회원 ID", defaultValue = "")
  private UUID blockTargetMemberId;

  @Schema(description = "회원 상품 카테고리 매핑 리스트", defaultValue = "")
  private List<Integer> preferredCategories;

  private Double longitude; // 경도
  private Double latitude; // 위도
  private String siDo; // 시/도
  private String siGunGu; // 시/군/구
  private String eupMyoenDong; // 읍/면/동
  private String ri; // 리

  @Schema(description = "탐색 범위 (단위: 미터)", defaultValue = "5000")
  private Double searchRadiusInMeters;

  @Schema(description = "닉네임")
  private String nickname;

  @Schema(description = "프로필 이미지 URL")
  private String profileUrl;

  @Schema(description = "조회할 회원 ID")
  private UUID memberId;

  @Schema(description = "마케팅 알림 동의 여부")
  private Boolean isMarketingInfoAgreed;

  @Schema(description = "활동 알림 동의 여부")
  private Boolean isActivityNotificationAgreed;

  @Schema(description = "채팅 알림 동의 여부")
  private Boolean isChatNotificationAgreed;

  @Schema(description = "콘텐츠 알림 동의 여부")
  private Boolean isContentNotificationAgreed;

  @Schema(description = "거래 알림 동의 여부")
  private Boolean isTradeNotificationAgreed;
}
