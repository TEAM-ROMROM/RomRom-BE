package com.romrom.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.romrom.application.dto.AdminResponse.AdminLabeledCount;
import com.romrom.common.constant.TradeStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 분포 라벨 변환 검증 (#827): 건수 많은 순, 같은 건수는 키 순, 변환 실패 시 키 그대로
 */
class AdminDashboardAnalyticsLabelTest {

  @Test
  @DisplayName("enum 설명으로 표시 이름, 건수 내림차순")
  void 분포는_표시_이름과_건수_순으로_정렬된다() {
    List<AdminLabeledCount> labeledCounts = AdminDashboardAnalyticsService.toLabeledCounts(
        Map.of("TRADED", 2L, "CHATTING", 5L, "PENDING", 2L),
        key -> TradeStatus.valueOf(key).getDescription());

    assertEquals(List.of("CHATTING", "PENDING", "TRADED"), labeledCounts.stream().map(AdminLabeledCount::getKey).toList());
    assertEquals("채팅중", labeledCounts.get(0).getLabel());
  }

  @Test
  @DisplayName("모르는 값(enum 추가 전 데이터 등)은 키를 표시 이름으로")
  void 변환_실패_시_키를_표시한다() {
    List<AdminLabeledCount> labeledCounts = AdminDashboardAnalyticsService.toLabeledCounts(
        Map.of("BRAND_NEW", 1L), key -> TradeStatus.valueOf(key).getDescription());

    assertEquals("BRAND_NEW", labeledCounts.get(0).getLabel());
  }
}
