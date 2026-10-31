package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 활동 이벤트 대분류 4개: 트래픽, 참여, 거래, 시스템 (시계열 그룹 집계용)
 */
@AllArgsConstructor
@Getter
public enum ActivityEventCategory {
  TRAFFIC("트래픽"),
  ENGAGEMENT("참여"),
  TRANSACTION("거래"),
  SYSTEM("시스템");

  private final String description;
}
