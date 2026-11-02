package com.romrom.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.romrom.application.dto.AdminResponse.AdminPerformanceSnapshot;
import com.romrom.application.dto.AdminResponse.AdminRequestMetric;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminPerformanceServiceTest {

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final AdminPerformanceService adminPerformanceService = new AdminPerformanceService(meterRegistry);

  private void recordRequest(String uri, String status, int callCount, long elapsedMilliseconds) {
    Timer requestTimer = Timer.builder("http.server.requests")
        .tag("method", "POST").tag("uri", uri).tag("status", status)
        .register(meterRegistry);
    for (int callIndex = 0; callIndex < callCount; callIndex++) {
      requestTimer.record(Duration.ofMillis(elapsedMilliseconds));
    }
  }

  private AdminPerformanceSnapshot takeSnapshot() {
    return adminPerformanceService.getPerformanceSnapshot().getPerformanceSnapshot();
  }

  @Test
  @DisplayName("상태 코드별 타이머를 API 하나로 합산하고 5xx를 따로 센다")
  void 상태_코드별_타이머를_합산한다() {
    recordRequest("/api/item/get", "200", 3, 100);
    recordRequest("/api/item/get", "500", 1, 500);

    List<AdminRequestMetric> busiestRequests = takeSnapshot().getBusiestRequests();

    assertEquals(1, busiestRequests.size());
    AdminRequestMetric requestMetric = busiestRequests.get(0);
    assertEquals(4L, requestMetric.getRequestCount());
    assertEquals(1L, requestMetric.getServerErrorCount());
    assertEquals(200.0, requestMetric.getAverageMilliseconds(), 0.01);
  }

  @Test
  @DisplayName("느린 API 순위는 평균 순이고 3회 미만 호출은 제외한다")
  void 느린_API_순위는_평균_순이다() {
    recordRequest("/api/fast", "200", 3, 10);
    recordRequest("/api/slow", "200", 3, 900);
    recordRequest("/api/once", "200", 1, 5000);

    List<AdminRequestMetric> slowestRequests = takeSnapshot().getSlowestRequests();

    assertEquals(List.of("/api/slow", "/api/fast"), slowestRequests.stream().map(AdminRequestMetric::getUri).toList());
  }

  @Test
  @DisplayName("헬스체크와 경로 매칭 실패 요청은 집계에서 뺀다")
  void 헬스체크와_매칭_실패는_제외한다() {
    recordRequest("/actuator/health", "200", 5, 5);
    recordRequest("UNKNOWN", "404", 5, 5);

    assertTrue(takeSnapshot().getBusiestRequests().isEmpty());
  }

  @Test
  @DisplayName("지표가 없는 환경에서도 0으로 응답한다")
  void 지표가_없어도_0으로_응답한다() {
    AdminPerformanceSnapshot performanceSnapshot = takeSnapshot();

    assertEquals(0L, performanceSnapshot.getHeapUsedBytes());
    assertEquals(0L, performanceSnapshot.getDatabaseConnectionTimeoutCount());
  }
}
