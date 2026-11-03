package com.romrom.common.service;

import com.romrom.common.constant.ActivityEventType;
import com.romrom.common.entity.mongo.UserActivityEvent;
import com.romrom.common.repository.UserActivityEventRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 활동 이벤트 단일 기록 진입점: 전용 실행기로 비동기 적재 (유실 허용, 실패·포화가 본 요청에 전파되지 않음)
 */
@Service
@Slf4j
public class ActivityEventRecorder {

  public static final String ACTIVITY_EVENT_EXECUTOR_BEAN_NAME = "activityEventExecutor";

  private final UserActivityEventRepository userActivityEventRepository;
  private final Executor activityEventExecutor;

  public ActivityEventRecorder(UserActivityEventRepository userActivityEventRepository,
      @Qualifier(ACTIVITY_EVENT_EXECUTOR_BEAN_NAME) Executor activityEventExecutor) {
    this.userActivityEventRepository = userActivityEventRepository;
    this.activityEventExecutor = activityEventExecutor;
  }

  public void record(ActivityEventType eventType, UUID memberId, Map<String, Object> properties) {
    recordEvent(UserActivityEvent.of(eventType, memberId, null, properties, LocalDateTime.now()));
  }

  public void recordMetric(ActivityEventType eventType, long metricValue) {
    recordEvent(UserActivityEvent.of(eventType, null, metricValue, null, LocalDateTime.now()));
  }

  private void recordEvent(UserActivityEvent userActivityEvent) {
    try {
      activityEventExecutor.execute(() -> saveQuietly(userActivityEvent));
    } catch (RejectedExecutionException rejectedExecutionException) {
      log.debug("[activity event] dropped : executor saturated, type={}", userActivityEvent.getEventType());
    }
  }

  private void saveQuietly(UserActivityEvent userActivityEvent) {
    try {
      userActivityEventRepository.save(userActivityEvent);
    } catch (RuntimeException saveException) {
      log.warn("[activity event] save failed : type={}, error={}", userActivityEvent.getEventType(), saveException.getMessage());
    }
  }
}
