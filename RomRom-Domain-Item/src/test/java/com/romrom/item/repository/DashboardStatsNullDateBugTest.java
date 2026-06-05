package com.romrom.item.repository;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository.TradeStatusCountProjection;
import com.romrom.item.repository.postgres.TradeReviewRepository;
import com.romrom.web.RomBackApplication;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 회귀 테스트: 관리자 대시보드 통계 쿼리가 기간 파라미터 둘 다 null일 때
 * PostgreSQL "could not determine data type of parameter $1" 로 깨지던 버그.
 *
 * 수정 전: (:startDate IS NULL OR ...) 패턴이 두 날짜 모두 null이면 PG가 $1 타입 추론 실패 → 500
 * 수정 후: CAST(:startDate AS timestamp) 로 타입을 명시해 정상 집계
 *
 * dev 프로파일(실제 PostgreSQL)로 실행해야 의미가 있다. H2는 이 버그를 재현하지 못한다.
 */
@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class DashboardStatsNullDateBugTest {

  @Autowired
  TradeRequestHistoryRepository tradeRequestHistoryRepository;

  @Autowired
  TradeReviewRepository tradeReviewRepository;

  @Test
  public void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::countGroupedByTradeStatus_기간_null이면_타입추론오류없이_집계반환_테스트);
    lineLog(null);
    timeLog(this::countByCreatedDateBetweenNullable_기간_null이면_타입추론오류없이_카운트반환_테스트);
    lineLog(null);

    lineLog("테스트종료");
  }

  // 대시보드 stats: 거래 상태별 집계 (startDate/endDate 둘 다 null = 전체 누적)
  public void countGroupedByTradeStatus_기간_null이면_타입추론오류없이_집계반환_테스트() {
    // 수정 전이면 이 호출에서 InvalidDataAccessResourceUsageException 발생
    List<TradeStatusCountProjection> counts =
        tradeRequestHistoryRepository.countGroupedByTradeStatus(null, null);

    // 예외 없이 리스트가 반환되면 통과 (건수 0이어도 정상)
    Assertions.assertNotNull(counts, "전체 누적 거래 상태 집계는 null이 아니어야 한다");
    lineLog("거래 상태별 집계 행 수: " + counts.size());
  }

  // 대시보드 stats: 신규 후기 카운트 (startDate/endDate 둘 다 null = 전체 누적)
  public void countByCreatedDateBetweenNullable_기간_null이면_타입추론오류없이_카운트반환_테스트() {
    long reviewCount = tradeReviewRepository.countByCreatedDateBetweenNullable(null, null);

    Assertions.assertTrue(reviewCount >= 0, "전체 누적 후기 수는 0 이상이어야 한다");
    lineLog("전체 누적 후기 수: " + reviewCount);
  }
}
