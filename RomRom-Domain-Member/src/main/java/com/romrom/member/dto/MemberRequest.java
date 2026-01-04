package com.romrom.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.romrom.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
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

  @Schema(description = "회원 상품 카테고리 매핑 리스트", defaultValue = "")
  private List<Integer> preferredCategories;

  private Double longitude; // 경도
  private Double latitude; // 위도
  private String siDo; // 시/도
  private String siGunGu; // 시/군/구
  private String eupMyoenDong; // 읍/면/동
  private String ri; // 리

  private Boolean isMarketingInfoAgreed;

  @Schema(description = "탐색 범위 (단위: 미터)", defaultValue = "5000")
  private Double searchRadiusInMeters;

  @Schema(description = "닉네임")
  private String nickname;

  @Schema(description = "프로필 이미지 URL")
  private String profileUrl;
}
