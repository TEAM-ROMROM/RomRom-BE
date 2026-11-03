package com.romrom.common.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.common.constant.ActivityEventCategory;
import com.romrom.common.constant.ActivityEventType;
import com.romrom.common.entity.mongo.UserActivityEvent;
import com.romrom.common.repository.UserActivityEventRepository;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 활동 이벤트 기록 단위 테스트: 대분류 자동 지정, 저장 실패·실행기 포화가 호출자에 전파되지 않음
 */
@ExtendWith(MockitoExtension.class)
class ActivityEventRecorderTest {

  // 테스트용 동기 실행기 (호출 스레드에서 바로 실행)
  private static final Executor DIRECT_EXECUTOR = Runnable::run;

  @Mock
  private UserActivityEventRepository userActivityEventRepository;

  @Test
  @DisplayName("기록하면 이벤트 종류의 대분류·회원·속성이 담겨 저장된다")
  void 이벤트_저장() {
    ActivityEventRecorder activityEventRecorder = new ActivityEventRecorder(userActivityEventRepository, DIRECT_EXECUTOR);
    UUID memberId = UUID.randomUUID();

    activityEventRecorder.record(ActivityEventType.API_CALL, memberId, Map.of("pathPattern", "/api/item/{itemId}"));

    ArgumentCaptor<UserActivityEvent> eventCaptor = ArgumentCaptor.forClass(UserActivityEvent.class);
    verify(userActivityEventRepository).save(eventCaptor.capture());
    Assertions.assertEquals(ActivityEventCategory.TRAFFIC, eventCaptor.getValue().getEventCategory());
    Assertions.assertEquals(memberId, eventCaptor.getValue().getMemberId());
    Assertions.assertEquals("/api/item/{itemId}", eventCaptor.getValue().getProperties().get("pathPattern"));
    Assertions.assertNotNull(eventCaptor.getValue().getOccurredAt());
  }

  @Test
  @DisplayName("스냅샷 기록은 metricValue에 값이 담긴다")
  void 스냅샷_저장() {
    ActivityEventRecorder activityEventRecorder = new ActivityEventRecorder(userActivityEventRepository, DIRECT_EXECUTOR);

    activityEventRecorder.recordMetric(ActivityEventType.CONCURRENT_USERS, 12L);

    ArgumentCaptor<UserActivityEvent> eventCaptor = ArgumentCaptor.forClass(UserActivityEvent.class);
    verify(userActivityEventRepository).save(eventCaptor.capture());
    Assertions.assertEquals(12L, eventCaptor.getValue().getMetricValue());
    Assertions.assertNull(eventCaptor.getValue().getMemberId());
  }

  @Test
  @DisplayName("저장 실패는 호출자에게 예외로 전파되지 않는다")
  void 저장_실패_격리() {
    when(userActivityEventRepository.save(any())).thenThrow(new IllegalStateException("mongo down"));
    ActivityEventRecorder activityEventRecorder = new ActivityEventRecorder(userActivityEventRepository, DIRECT_EXECUTOR);

    Assertions.assertDoesNotThrow(() -> activityEventRecorder.record(ActivityEventType.API_CALL, null, null));
  }

  @Test
  @DisplayName("실행기 포화(거부)는 호출자에게 예외로 전파되지 않는다")
  void 실행기_포화_격리() {
    Executor rejectingExecutor = task -> {
      throw new RejectedExecutionException("saturated");
    };
    ActivityEventRecorder activityEventRecorder = new ActivityEventRecorder(userActivityEventRepository, rejectingExecutor);

    Assertions.assertDoesNotThrow(() -> activityEventRecorder.record(ActivityEventType.API_CALL, null, null));
  }
}
