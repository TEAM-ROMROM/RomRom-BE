package com.romrom.notification.service;

import com.romrom.notification.entity.HomePopup;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 노출할 홈 팝업 1건 선택: isActive true > 기간 안(null 경계는 열림) > priority 높은 순 > 최신 등록순
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HomePopupSelector {

  // 오름차순 (priority > createdDate) > max가 최우선 팝업
  private static final Comparator<HomePopup> PRIORITY_THEN_CREATED_DATE = Comparator
      .comparing((HomePopup homePopup) -> homePopup.getPriority() == null ? 0 : homePopup.getPriority())
      .thenComparing(HomePopup::getCreatedDate, Comparator.nullsFirst(Comparator.naturalOrder()));

  public static Optional<HomePopup> selectActiveHomePopup(Collection<HomePopup> homePopupCandidates, LocalDateTime now) {
    return homePopupCandidates.stream()
        .filter(homePopup -> isDisplayableAt(homePopup, now))
        .max(PRIORITY_THEN_CREATED_DATE);
  }

  /**
   * 노출 가능 여부 (활성 + startDate <= now <= endDate)
   */
  public static boolean isDisplayableAt(HomePopup homePopup, LocalDateTime now) {
    if (!Boolean.TRUE.equals(homePopup.getIsActive())) {
      return false;
    }
    boolean hasStarted = homePopup.getStartDate() == null || !homePopup.getStartDate().isAfter(now);
    boolean hasNotEnded = homePopup.getEndDate() == null || !homePopup.getEndDate().isBefore(now);
    return hasStarted && hasNotEnded;
  }
}
