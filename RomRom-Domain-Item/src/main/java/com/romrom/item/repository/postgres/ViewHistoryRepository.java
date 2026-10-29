package com.romrom.item.repository.postgres;

import com.romrom.common.dto.DailyCountProjection;
import com.romrom.item.entity.postgres.ViewHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ViewHistoryRepository extends JpaRepository<ViewHistory, UUID> {

  boolean existsByMemberMemberIdAndItemItemIdAndViewedDate(UUID memberId, UUID itemId, LocalDate viewedDate);

  // ===== 관리자 대시보드 분석 (#827) =====

  /**
   * 날짜별 물품 조회 수 (조회일 기준)
   */
  @Query(value = "SELECT TO_CHAR(viewed_date, 'YYYY-MM-DD') AS day, COUNT(*) AS count FROM view_history "
      + "WHERE viewed_date BETWEEN :startDate AND :endDate GROUP BY TO_CHAR(viewed_date, 'YYYY-MM-DD')",
      nativeQuery = true)
  List<DailyCountProjection> countDailyViews(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
