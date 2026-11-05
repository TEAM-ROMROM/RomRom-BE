package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 푸시 캠페인 대상 (#874): 전체, 조건 필터, 회원 지정, 관리자 본인 테스트
 */
@AllArgsConstructor
@Getter
public enum PushCampaignTargetType {
  ALL("전체"),
  SEGMENT("조건"),
  MEMBERS("회원 지정"),
  TEST("테스트");

  private final String description;
}
