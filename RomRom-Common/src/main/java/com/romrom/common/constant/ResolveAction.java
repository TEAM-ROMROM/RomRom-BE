package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 신고 원스톱 처리 액션 (#709)
 * - 신고 상세 화면에서 한 번에 후속 조치까지 끝내기 위한 액션 종류
 */
@AllArgsConstructor
@Getter
public enum ResolveAction {
  SUSPEND_MEMBER("피신고자 정지"),   // 피신고자 회원 정지 + 신고 COMPLETED
  DELETE_ITEM("신고 물품 삭제"),     // 신고 대상 물품 삭제 + 신고 COMPLETED
  REJECT("신고 반려");              // 액션 없이 신고 REJECTED

  private final String description;
}
