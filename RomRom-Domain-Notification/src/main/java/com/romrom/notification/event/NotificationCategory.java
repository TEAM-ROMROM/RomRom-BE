package com.romrom.notification.event;

import com.romrom.member.entity.Member;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationCategory {

  TRADE(Member::getIsTradeNotificationAgreed),
  CHAT(Member::getIsChatNotificationAgreed),
  ACTIVITY(Member::getIsActivityNotificationAgreed),
  CONTENT(Member::getIsContentNotificationAgreed),
  MARKETING(Member::getIsMarketingInfoAgreed);

  private final Function<Member, Boolean> pushEnabledChecker;

  /**
   * 해당 회원이 이 카테고리의 푸시 알림을 허용했는지 확인
   */
  public boolean isPushEnabled(Member member) {
    return Boolean.TRUE.equals(pushEnabledChecker.apply(member));
  }
}
