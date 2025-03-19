package com.romrom.romback.domain.object.dto;

import com.romrom.romback.domain.object.postgres.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class LocationRequest {
  private Member member;
  private Double longitude; // 경도
  private Double latitude; // 위도
  private String siDo; // 시/도
  private String siGunGu; // 시/군/구
  private String eupMyoenDong; // 읍/면/동
  private String ri; // 리
  private String fullAddress; // 지번 주소
  private String roadAddress; // 도로명 주소
}
