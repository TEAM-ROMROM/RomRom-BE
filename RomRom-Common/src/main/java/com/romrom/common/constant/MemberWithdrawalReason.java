package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 회원 탈퇴 사유 6개 (선택 수집, 구버전 앱은 미전송 > null)
 */
@Getter
@AllArgsConstructor
public enum MemberWithdrawalReason {
  RARELY_USED("자주 사용하지 않아요"),
  NO_WANTED_ITEMS("원하는 물건이 없어요"),
  UNSATISFYING_TRADE("교환 경험이 만족스럽지 않았어요"),
  INCONVENIENT_SERVICE("앱 사용이 불편해요"),
  PRIVACY_CONCERN("개인정보가 걱정돼요"),
  OTHER("기타");

  private final String description;
}
