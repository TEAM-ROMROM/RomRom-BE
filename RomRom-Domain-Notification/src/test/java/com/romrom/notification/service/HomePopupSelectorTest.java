package com.romrom.notification.service;

import com.romrom.notification.entity.HomePopup;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 활성 홈 팝업 선택 단위 테스트 (#749): 기간, 활성 여부, 우선순위, 없음
 */
class HomePopupSelectorTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0);

  private static HomePopup createHomePopup(String title, int priority, boolean isActive,
      LocalDateTime startDate, LocalDateTime endDate, LocalDateTime createdDate) {
    HomePopup homePopup = HomePopup.builder()
        .title(title)
        .imageUrl("https://cdn.example.com/" + title + ".webp")
        .priority(priority)
        .isActive(isActive)
        .startDate(startDate)
        .endDate(endDate)
        .build();
    homePopup.setCreatedDate(createdDate);
    return homePopup;
  }

  @Test
  @DisplayName("후보가 없으면 빈 결과")
  void selectActiveHomePopup_후보없음_빈결과() {
    Assertions.assertTrue(HomePopupSelector.selectActiveHomePopup(List.of(), NOW).isEmpty());
  }

  @Test
  @DisplayName("비활성 팝업은 기간 안이어도 선택하지 않음")
  void selectActiveHomePopup_비활성_제외() {
    HomePopup inactiveHomePopup = createHomePopup("inactive", 100, false, null, null, NOW.minusDays(1));

    Assertions.assertTrue(HomePopupSelector.selectActiveHomePopup(List.of(inactiveHomePopup), NOW).isEmpty());
  }

  @Test
  @DisplayName("기간 전·기간 후 팝업 제외, null 경계는 열린 기간")
  void selectActiveHomePopup_기간판정() {
    HomePopup notStartedHomePopup = createHomePopup("notStarted", 100, true, NOW.plusMinutes(1), null, NOW.minusDays(1));
    HomePopup endedHomePopup = createHomePopup("ended", 90, true, null, NOW.minusMinutes(1), NOW.minusDays(1));
    HomePopup openPeriodHomePopup = createHomePopup("openPeriod", 1, true, null, null, NOW.minusDays(1));

    Optional<HomePopup> selectedHomePopup = HomePopupSelector.selectActiveHomePopup(
        List.of(notStartedHomePopup, endedHomePopup, openPeriodHomePopup), NOW);

    Assertions.assertEquals("openPeriod", selectedHomePopup.orElseThrow().getTitle());
  }

  @Test
  @DisplayName("시작·종료 경계 시각 정각은 노출")
  void isDisplayableAt_경계포함() {
    HomePopup boundaryHomePopup = createHomePopup("boundary", 0, true, NOW, NOW, NOW.minusDays(1));

    Assertions.assertTrue(HomePopupSelector.isDisplayableAt(boundaryHomePopup, NOW));
  }

  @Test
  @DisplayName("우선순위 높은 팝업 선택, 동률이면 최근 등록 팝업")
  void selectActiveHomePopup_우선순위_동률최신() {
    HomePopup lowPriorityHomePopup = createHomePopup("low", 1, true, null, null, NOW.minusHours(1));
    HomePopup olderHighPriorityHomePopup = createHomePopup("olderHigh", 10, true, null, null, NOW.minusDays(2));
    HomePopup newerHighPriorityHomePopup = createHomePopup("newerHigh", 10, true, null, null, NOW.minusDays(1));

    Optional<HomePopup> selectedHomePopup = HomePopupSelector.selectActiveHomePopup(
        List.of(lowPriorityHomePopup, olderHighPriorityHomePopup, newerHighPriorityHomePopup), NOW);

    Assertions.assertEquals("newerHigh", selectedHomePopup.orElseThrow().getTitle());
  }
}
