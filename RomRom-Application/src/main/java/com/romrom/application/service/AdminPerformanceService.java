package com.romrom.application.service;

import com.romrom.application.dto.AdminResponse;
import com.romrom.application.dto.AdminResponse.AdminPerformanceSnapshot;
import com.romrom.application.dto.AdminResponse.AdminRequestMetric;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 관리자 성능 지표: 서버 메모리·GC, DB 커넥션, API별 응답 시간 (서버 기동 후 누적)
 * 외부 수집기 없이 앱 내부 지표를 그대로 조회
 */
@Service
@RequiredArgsConstructor
public class AdminPerformanceService {

  // 화면에 보여 줄 API 수 (느린 순·많이 호출된 순 각각)
  private static final int MAXIMUM_REQUEST_METRIC_COUNT = 15;
  // 응답 시간 순위 대상 최소 호출 수 (1회성 호출이 순위를 차지하는 것 방지)
  private static final long MINIMUM_CALL_COUNT_FOR_SLOWEST_RANKING = 3;
  // 집계 제외 경로 접두사 (헬스체크가 순위를 왜곡)
  private static final String ACTUATOR_PATH_PREFIX = "/actuator";
  // 정적 파일 경로 패턴 접미사 (/js/** 등)
  private static final String STATIC_RESOURCE_PATTERN_SUFFIX = "/**";
  // 경로 매칭 실패 요청 (스캐너 등) 표시값
  private static final String UNMATCHED_URI_TAG = "UNKNOWN";
  private static final String SERVER_ERROR_STATUS_PREFIX = "5";

  private final MeterRegistry meterRegistry;

  /**
   * 성능 지표 1회 수집: JVM > DB 커넥션 > API별 응답 시간
   */
  public AdminResponse getPerformanceSnapshot() {
    List<AdminRequestMetric> requestMetrics = collectRequestMetrics();

    AdminPerformanceSnapshot performanceSnapshot = AdminPerformanceSnapshot.builder()
        .uptimeSeconds(readGaugeTotal("process.uptime").longValue())
        .heapUsedBytes(readHeapBytes("jvm.memory.used"))
        .heapMaxBytes(readHeapBytes("jvm.memory.max"))
        .liveThreadCount(readGaugeTotal("jvm.threads.live").longValue())
        .gcPauseCount(meterRegistry.find("jvm.gc.pause").timers().stream().mapToLong(Timer::count).sum())
        .gcPauseTotalMilliseconds(sumTotalMilliseconds(meterRegistry.find("jvm.gc.pause").timers()))
        .databaseConnectionActiveCount(readGaugeTotal("hikaricp.connections.active").longValue())
        .databaseConnectionIdleCount(readGaugeTotal("hikaricp.connections.idle").longValue())
        .databaseConnectionPendingCount(readGaugeTotal("hikaricp.connections.pending").longValue())
        .databaseConnectionMaxCount(readGaugeTotal("hikaricp.connections.max").longValue())
        .databaseConnectionTimeoutCount(readCounterTotal("hikaricp.connections.timeout"))
        .slowestRequests(rankSlowestRequests(requestMetrics))
        .busiestRequests(rankBusiestRequests(requestMetrics))
        .build();

    return AdminResponse.builder().performanceSnapshot(performanceSnapshot).build();
  }

  /**
   * http.server.requests 타이머 > 메서드+경로별 합산 (상태 코드별로 나뉜 타이머를 하나로)
   */
  private List<AdminRequestMetric> collectRequestMetrics() {
    Map<String, AdminRequestMetric> requestMetricByEndpoint = new LinkedHashMap<>();
    for (Timer requestTimer : meterRegistry.find("http.server.requests").timers()) {
      String uriTag = requestTimer.getId().getTag("uri");
      if (!isApplicationEndpoint(uriTag)) {
        continue;
      }
      String methodTag = requestTimer.getId().getTag("method");
      AdminRequestMetric requestMetric = requestMetricByEndpoint.computeIfAbsent(methodTag + " " + uriTag,
          endpointKey -> AdminRequestMetric.builder()
              .method(methodTag).uri(uriTag)
              .requestCount(0L).serverErrorCount(0L)
              .totalMilliseconds(0.0).averageMilliseconds(0.0).recentMaximumMilliseconds(0.0)
              .build());
      mergeTimerIntoRequestMetric(requestMetric, requestTimer);
    }
    return List.copyOf(requestMetricByEndpoint.values());
  }

  // 집계 제외 3종: 경로 매칭 실패, 헬스체크, 정적 파일
  private boolean isApplicationEndpoint(String uriTag) {
    return uriTag != null
        && !UNMATCHED_URI_TAG.equals(uriTag)
        && !uriTag.startsWith(ACTUATOR_PATH_PREFIX)
        && !uriTag.endsWith(STATIC_RESOURCE_PATTERN_SUFFIX);
  }

  private void mergeTimerIntoRequestMetric(AdminRequestMetric requestMetric, Timer requestTimer) {
    requestMetric.setRequestCount(requestMetric.getRequestCount() + requestTimer.count());
    requestMetric.setTotalMilliseconds(requestMetric.getTotalMilliseconds() + requestTimer.totalTime(TimeUnit.MILLISECONDS));
    requestMetric.setRecentMaximumMilliseconds(
        Math.max(requestMetric.getRecentMaximumMilliseconds(), requestTimer.max(TimeUnit.MILLISECONDS)));
    String statusTag = requestTimer.getId().getTag("status");
    if (statusTag != null && statusTag.startsWith(SERVER_ERROR_STATUS_PREFIX)) {
      requestMetric.setServerErrorCount(requestMetric.getServerErrorCount() + requestTimer.count());
    }
    if (requestMetric.getRequestCount() > 0) {
      requestMetric.setAverageMilliseconds(requestMetric.getTotalMilliseconds() / requestMetric.getRequestCount());
    }
  }

  private List<AdminRequestMetric> rankSlowestRequests(List<AdminRequestMetric> requestMetrics) {
    return requestMetrics.stream()
        .filter(requestMetric -> requestMetric.getRequestCount() >= MINIMUM_CALL_COUNT_FOR_SLOWEST_RANKING)
        .sorted(Comparator.comparingDouble(AdminRequestMetric::getAverageMilliseconds).reversed())
        .limit(MAXIMUM_REQUEST_METRIC_COUNT)
        .toList();
  }

  private List<AdminRequestMetric> rankBusiestRequests(List<AdminRequestMetric> requestMetrics) {
    return requestMetrics.stream()
        .sorted(Comparator.comparingLong(AdminRequestMetric::getRequestCount).reversed())
        .limit(MAXIMUM_REQUEST_METRIC_COUNT)
        .toList();
  }

  private double sumTotalMilliseconds(Collection<Timer> timers) {
    return timers.stream().mapToDouble(timer -> timer.totalTime(TimeUnit.MILLISECONDS)).sum();
  }

  // 메모리 영역별 게이지 합산 (상한 미정 영역의 -1 제외)
  private long readHeapBytes(String gaugeName) {
    return (long) meterRegistry.find(gaugeName).tag("area", "heap").gauges().stream()
        .mapToDouble(Gauge::value)
        .filter(heapBytes -> heapBytes >= 0)
        .sum();
  }

  // 지표 없으면 0 (테스트·풀 미초기화 환경)
  private Double readGaugeTotal(String gaugeName) {
    return meterRegistry.find(gaugeName).gauges().stream().mapToDouble(Gauge::value).sum();
  }

  private long readCounterTotal(String timerName) {
    return meterRegistry.find(timerName).counters().stream().mapToLong(counter -> (long) counter.count()).sum();
  }
}
