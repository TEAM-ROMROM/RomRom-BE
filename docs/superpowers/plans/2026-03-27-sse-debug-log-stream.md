# SSE 서버 로그 스트리밍 디버그 엔드포인트 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 테스트 빌드 앱에서 서버 로그를 실시간으로 확인할 수 있는 SSE 스트리밍 API 엔드포인트를 추가한다.

**Architecture:** Logback 커스텀 Appender가 로그 이벤트를 캡처하여 SseLogBroadcaster에 발행하고, SseLogBroadcaster가 연결된 모든 SseEmitter 구독자에게 이벤트를 브로드캐스트한다. DebugController가 SSE 연결을 수립하고 @SecuredApi HMAC 검증으로 인증한다.

**Tech Stack:** Spring Boot 3.4.1, SseEmitter, Logback (ch.qos.logback), Jackson, @SecuredApi AOP (기존 인프라)

**관련 이슈:** https://github.com/TEAM-ROMROM/RomRom-BE/issues/607

**설계 문서:** `docs/superpowers/specs/2026-03-27-sse-debug-log-stream-design.md`

---

## 파일 구조

### 신규 생성

| 파일 | 모듈 | 역할 |
|------|------|------|
| `RomRom-Common/src/main/java/com/romrom/common/dto/DebugLogEvent.java` | Common | 로그 이벤트 DTO |
| `RomRom-Common/src/main/java/com/romrom/common/service/SseLogBroadcaster.java` | Common | SSE 구독자 관리 + 브로드캐스트 |
| `RomRom-Common/src/main/java/com/romrom/common/logging/SseLogAppender.java` | Common | Logback 커스텀 Appender |
| `RomRom-Web/src/main/java/com/romrom/web/controller/api/DebugController.java` | Web | SSE 엔드포인트 |
| `RomRom-Web/src/main/java/com/romrom/web/controller/api/DebugControllerDocs.java` | Web | Swagger 문서화 인터페이스 |
| `RomRom-Web/src/main/resources/logback-spring.xml` | Web | Logback 설정 (커스텀 Appender 등록) |

### 수정

| 파일 | 변경 내용 |
|------|----------|
| `RomRom-Domain-Auth/src/main/java/com/romrom/auth/dto/SecurityUrls.java` | `SECURED_API_URLS`에 `/api/app/debug/log-stream` 추가 |

---

## Task 1: DebugLogEvent DTO 생성

**Files:**
- Create: `RomRom-Common/src/main/java/com/romrom/common/dto/DebugLogEvent.java`

- [ ] **Step 1: DebugLogEvent 클래스 작성**

```java
package com.romrom.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * SSE 로그 스트리밍용 로그 이벤트 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class DebugLogEvent {

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
  private final LocalDateTime timestamp;

  private final String level;

  private final String loggerName;

  private final String message;

  private final String threadName;
}
```

- [ ] **Step 2: 커밋**

```
테스트_빌드용_SSE_서버_로그_스트리밍_엔드포인트_추가 : feat : DebugLogEvent DTO 생성 https://github.com/TEAM-ROMROM/RomRom-BE/issues/607
```

---

## Task 2: SseLogBroadcaster 구현

**Files:**
- Create: `RomRom-Common/src/main/java/com/romrom/common/service/SseLogBroadcaster.java`

- [ ] **Step 1: SseLogBroadcaster 클래스 작성**

```java
package com.romrom.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.romrom.common.dto.DebugLogEvent;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 로그 스트리밍 구독자 관리 및 브로드캐스트
 * - 최대 동시 구독자: 5
 * - 초당 로그 이벤트 제한: 100건
 */
@Component
@Slf4j
public class SseLogBroadcaster {

  private static final int MAX_SUBSCRIBERS = 5;
  private static final int MAX_EVENTS_PER_SECOND = 100;

  private final CopyOnWriteArrayList<SseEmitter> debugLogSubscribers = new CopyOnWriteArrayList<>();
  private final ObjectMapper debugLogObjectMapper;

  private final AtomicInteger eventsInCurrentSecond = new AtomicInteger(0);
  private final AtomicLong currentSecondTimestamp = new AtomicLong(0);
  private final AtomicInteger skippedEventCount = new AtomicInteger(0);

  public SseLogBroadcaster() {
    this.debugLogObjectMapper = new ObjectMapper();
    this.debugLogObjectMapper.registerModule(new JavaTimeModule());
  }

  /**
   * SSE 구독자 등록
   * @return 등록 성공 여부 (최대 구독자 초과 시 false)
   */
  public boolean addSubscriber(SseEmitter sseLogEmitter) {
    if (debugLogSubscribers.size() >= MAX_SUBSCRIBERS) {
      log.warn("SSE 로그 스트리밍 최대 구독자 수 초과: {}/{}", debugLogSubscribers.size(), MAX_SUBSCRIBERS);
      return false;
    }
    debugLogSubscribers.add(sseLogEmitter);
    log.info("SSE 로그 스트리밍 구독자 등록 (현재: {}명)", debugLogSubscribers.size());
    return true;
  }

  /**
   * SSE 구독자 제거
   */
  public void removeSubscriber(SseEmitter sseLogEmitter) {
    debugLogSubscribers.remove(sseLogEmitter);
    log.info("SSE 로그 스트리밍 구독자 제거 (현재: {}명)", debugLogSubscribers.size());
  }

  /**
   * 모든 구독자에게 로그 이벤트 브로드캐스트
   * - 초당 100건 제한, 초과 시 건너뛰고 "[N건 생략]" 메시지 전송
   */
  public void broadcast(DebugLogEvent debugLogEvent) {
    if (debugLogSubscribers.isEmpty()) {
      return;
    }

    // Rate limiting: 초당 이벤트 수 체크
    long nowSeconds = System.currentTimeMillis() / 1000;
    long previousSecond = currentSecondTimestamp.getAndSet(nowSeconds);
    if (nowSeconds != previousSecond) {
      // 새로운 초가 시작됨 — 생략된 건수가 있으면 알림 전송
      int skippedInPreviousSecond = skippedEventCount.getAndSet(0);
      if (skippedInPreviousSecond > 0) {
        sendSkippedNotification(skippedInPreviousSecond);
      }
      eventsInCurrentSecond.set(0);
    }

    if (eventsInCurrentSecond.incrementAndGet() > MAX_EVENTS_PER_SECOND) {
      skippedEventCount.incrementAndGet();
      return;
    }

    String debugLogJson;
    try {
      debugLogJson = debugLogObjectMapper.writeValueAsString(debugLogEvent);
    } catch (IOException e) {
      return;
    }

    for (SseEmitter subscriberEmitter : debugLogSubscribers) {
      try {
        subscriberEmitter.send(SseEmitter.event().data(debugLogJson));
      } catch (IOException e) {
        debugLogSubscribers.remove(subscriberEmitter);
      }
    }
  }

  /**
   * 생략된 로그 건수 알림 전송
   */
  private void sendSkippedNotification(int skippedLogCount) {
    String skippedMessage = String.format("{\"level\":\"WARN\",\"message\":\"[%d건 생략 — rate limit 초과]\"}", skippedLogCount);
    for (SseEmitter subscriberEmitter : debugLogSubscribers) {
      try {
        subscriberEmitter.send(SseEmitter.event().data(skippedMessage));
      } catch (IOException e) {
        debugLogSubscribers.remove(subscriberEmitter);
      }
    }
  }

  /**
   * 현재 활성 구독자 수 반환
   */
  public int getActiveSubscriberCount() {
    return debugLogSubscribers.size();
  }
}
```

- [ ] **Step 2: 커밋**

```
테스트_빌드용_SSE_서버_로그_스트리밍_엔드포인트_추가 : feat : SseLogBroadcaster 구독자 관리 컴포넌트 구현 https://github.com/TEAM-ROMROM/RomRom-BE/issues/607
```

---

## Task 3: SseLogAppender 구현

**Files:**
- Create: `RomRom-Common/src/main/java/com/romrom/common/logging/SseLogAppender.java`

- [ ] **Step 1: SseLogAppender 클래스 작성**

Logback Appender는 Spring Bean이 아니므로 `ApplicationContext`에서 `SseLogBroadcaster` 빈을 직접 가져온다.

```java
package com.romrom.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.romrom.common.dto.DebugLogEvent;
import com.romrom.common.service.SseLogBroadcaster;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.context.ApplicationContext;

/**
 * Logback 커스텀 Appender — 로그 이벤트를 SSE 구독자에게 발행
 * com.romrom 패키지 로그만 필터링하여 전송
 */
public class SseLogAppender extends AppenderBase<ILoggingEvent> {

  private static final String TARGET_LOGGER_PREFIX = "com.romrom";

  private static ApplicationContext applicationContext;
  private SseLogBroadcaster sseLogBroadcaster;

  /**
   * Spring ApplicationContext 설정 (ApplicationContextAware 또는 초기화 시 호출)
   */
  public static void setApplicationContext(ApplicationContext applicationContext) {
    SseLogAppender.applicationContext = applicationContext;
  }

  @Override
  public void start() {
    super.start();
  }

  @Override
  protected void append(ILoggingEvent loggingEvent) {
    // com.romrom 패키지 로그만 처리
    if (!loggingEvent.getLoggerName().startsWith(TARGET_LOGGER_PREFIX)) {
      return;
    }

    // 지연 초기화: Spring Context가 준비된 후 빈 조회
    if (sseLogBroadcaster == null) {
      if (applicationContext == null) {
        return;
      }
      try {
        sseLogBroadcaster = applicationContext.getBean(SseLogBroadcaster.class);
      } catch (Exception e) {
        return;
      }
    }

    DebugLogEvent debugLogEvent = DebugLogEvent.builder()
        .timestamp(LocalDateTime.ofInstant(
            Instant.ofEpochMilli(loggingEvent.getTimeStamp()),
            ZoneId.systemDefault()))
        .level(loggingEvent.getLevel().toString())
        .loggerName(loggingEvent.getLoggerName())
        .message(loggingEvent.getFormattedMessage())
        .threadName(loggingEvent.getThreadName())
        .build();

    sseLogBroadcaster.broadcast(debugLogEvent);
  }
}
```

- [ ] **Step 2: 커밋**

```
테스트_빌드용_SSE_서버_로그_스트리밍_엔드포인트_추가 : feat : SseLogAppender Logback 커스텀 Appender 구현 https://github.com/TEAM-ROMROM/RomRom-BE/issues/607
```

---

## Task 4: logback-spring.xml 생성 및 SseLogAppender 초기화

**Files:**
- Create: `RomRom-Web/src/main/resources/logback-spring.xml`

현재 프로젝트에 `logback-spring.xml`이 없고 `application.yml`의 `logging` 설정만 사용 중이다. `logback-spring.xml`을 생성하되, 기존 application.yml의 logging 설정을 그대로 유지하면서 SseLogAppender만 추가한다.

- [ ] **Step 1: logback-spring.xml 작성**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

  <!-- Spring Boot 기본 logback 설정 포함 (console, file 등) -->
  <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
  <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

  <!-- application.yml의 logging.file.name 프로퍼티 사용 -->
  <springProperty scope="context" name="LOG_FILE" source="logging.file.name" defaultValue="romrom.log"/>

  <!-- 파일 Appender (기존 application.yml 설정 반영) -->
  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_FILE}</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
      <fileNamePattern>${LOG_FILE}.%d{yyyy-MM-dd}.%i.gz</fileNamePattern>
      <maxFileSize>100MB</maxFileSize>
      <maxHistory>30</maxHistory>
    </rollingPolicy>
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- SSE 로그 스트리밍 Appender -->
  <appender name="SSE_LOG" class="com.romrom.common.logging.SseLogAppender"/>

  <!-- 프레임워크 로그 레벨 (기존 application.yml 설정 유지) -->
  <logger name="org.springframework" level="WARN"/>
  <logger name="org.hibernate" level="WARN"/>
  <logger name="org.springdoc" level="WARN"/>
  <logger name="org.springframework.boot.autoconfigure.logging" level="OFF"/>

  <!-- suh-logger 라이브러리 -->
  <logger name="me.suhsaechan.suh-logger" level="DEBUG"/>
  <logger name="me.suhsaechan.suh-api-log" level="DEBUG"/>

  <!-- 애플리케이션 로그 -->
  <logger name="com.romrom" level="DEBUG"/>

  <!-- Root 로거 -->
  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE"/>
    <appender-ref ref="SSE_LOG"/>
  </root>

</configuration>
```

- [ ] **Step 2: SseLogAppender에 ApplicationContext를 주입하는 초기화 코드 추가**

`SseLogAppender`는 Spring Bean이 아니므로, ApplicationContext를 static으로 주입해야 한다. `@Component`로 초기화 클래스를 만든다.

**Create:** `RomRom-Common/src/main/java/com/romrom/common/logging/SseLogAppenderInitializer.java`

```java
package com.romrom.common.logging;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Spring Context 초기화 시 SseLogAppender에 ApplicationContext를 주입
 */
@Component
public class SseLogAppenderInitializer implements ApplicationListener<ContextRefreshedEvent> {

  @Override
  public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
    ApplicationContext applicationContext = contextRefreshedEvent.getApplicationContext();
    SseLogAppender.setApplicationContext(applicationContext);
  }
}
```

- [ ] **Step 3: 커밋**

```
테스트_빌드용_SSE_서버_로그_스트리밍_엔드포인트_추가 : feat : logback-spring.xml 생성 및 SseLogAppender 초기화 https://github.com/TEAM-ROMROM/RomRom-BE/issues/607
```

---

## Task 5: SecurityUrls에 디버그 엔드포인트 등록

**Files:**
- Modify: `RomRom-Domain-Auth/src/main/java/com/romrom/auth/dto/SecurityUrls.java:63-66`

- [ ] **Step 1: SECURED_API_URLS에 디버그 엔드포인트 추가**

기존:
```java
  public static final List<String> SECURED_API_URLS = Arrays.asList(
    "/api/app/version/check",   // 앱 버전 체크
    "/api/app/version/update"   // 앱 최신 버전 업데이트 (CI/CD)
  );
```

변경:
```java
  public static final List<String> SECURED_API_URLS = Arrays.asList(
    "/api/app/version/check",   // 앱 버전 체크
    "/api/app/version/update",  // 앱 최신 버전 업데이트 (CI/CD)
    "/api/app/debug/log-stream" // SSE 로그 스트리밍 (테스트 빌드 디버그)
  );
```

- [ ] **Step 2: 커밋**

```
테스트_빌드용_SSE_서버_로그_스트리밍_엔드포인트_추가 : feat : SecurityUrls에 디버그 SSE 엔드포인트 등록 https://github.com/TEAM-ROMROM/RomRom-BE/issues/607
```

---

## Task 6: DebugControllerDocs Swagger 문서화 인터페이스 생성

**Files:**
- Create: `RomRom-Web/src/main/java/com/romrom/web/controller/api/DebugControllerDocs.java`

- [ ] **Step 1: DebugControllerDocs 인터페이스 작성**

```java
package com.romrom.web.controller.api;

import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface DebugControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.03.27", author = Author.SUHSAECHAN, issueNumber = 607, description = "SSE 서버 로그 스트리밍 디버그 엔드포인트 추가"),
  })
  @Operation(
      summary = "서버 로그 실시간 스트리밍 (SSE)",
      description = """
      ## 인증: **@SecuredApi** (HMAC + Timestamp)

      ## 요청 헤더
      - **`X-Timestamp`**: 밀리초 단위 타임스탬프
      - **`X-Signature`**: HMAC-SHA256(timestamp, secretKey) Hex 인코딩

      ## 반환값
      - **Content-Type**: `text/event-stream`
      - SSE 스트림으로 서버 로그 이벤트를 실시간 전송

      ## SSE 이벤트 포맷
      ```json
      {
        "timestamp": "2026-03-27T14:30:00.123",
        "level": "DEBUG",
        "loggerName": "com.romrom.web.controller.api.ItemController",
        "message": "물품 조회 요청 - memberId: 123",
        "threadName": "http-nio-8080-exec-1"
      }
      ```

      ## 동작 설명
      - 연결 후 서버의 전체 애플리케이션 로그(DEBUG/INFO/WARN/ERROR)를 실시간 스트리밍
      - com.romrom 패키지 로그만 전송 (프레임워크 로그 제외)
      - 5분(300초) 후 자동 연결 종료 (서버 리소스 보호)
      - 클라이언트가 연결을 종료하면 즉시 정리
      - 최대 동시 접속: 5명
      - 초당 최대 100건 (초과 시 "[N건 생략]" 메시지 전송)

      ## 에러코드
      - MISSING_SIGNATURE_HEADER (401): 서명 헤더 누락
      - EXPIRED_SIGNATURE_TIMESTAMP (401): 타임스탬프 만료
      - INVALID_SIGNATURE (401): 유효하지 않은 서명
      - 503: 최대 동시 접속 초과
      """
  )
  SseEmitter streamDebugLog();
}
```

- [ ] **Step 2: 커밋**

```
테스트_빌드용_SSE_서버_로그_스트리밍_엔드포인트_추가 : feat : DebugControllerDocs Swagger 문서화 인터페이스 생성 https://github.com/TEAM-ROMROM/RomRom-BE/issues/607
```

---

## Task 7: DebugController 구현

**Files:**
- Create: `RomRom-Web/src/main/java/com/romrom/web/controller/api/DebugController.java`

- [ ] **Step 1: DebugController 클래스 작성**

```java
package com.romrom.web.controller.api;

import com.romrom.common.annotation.SecuredApi;
import com.romrom.common.service.SseLogBroadcaster;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 디버그용 SSE 로그 스트리밍 엔드포인트
 * 테스트 빌드에서 앱 내 플로팅 버튼으로 서버 로그를 실시간 확인
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "디버그 API", description = "테스트 빌드용 디버그 도구 API")
@RequestMapping("/api/app")
@Slf4j
public class DebugController implements DebugControllerDocs {

  private static final long SSE_LOG_STREAM_TIMEOUT = 300_000L; // 5분

  private final SseLogBroadcaster sseLogBroadcaster;

  @Override
  @GetMapping(value = "/debug/log-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @SecuredApi
  public SseEmitter streamDebugLog() {
    SseEmitter debugLogEmitter = new SseEmitter(SSE_LOG_STREAM_TIMEOUT);

    boolean subscriberRegistered = sseLogBroadcaster.addSubscriber(debugLogEmitter);
    if (!subscriberRegistered) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "최대 동시 접속 수 초과");
    }

    // 연결 종료/타임아웃/에러 시 구독자 제거
    debugLogEmitter.onCompletion(() -> {
      sseLogBroadcaster.removeSubscriber(debugLogEmitter);
      log.debug("SSE 로그 스트리밍 연결 완료");
    });
    debugLogEmitter.onTimeout(() -> {
      sseLogBroadcaster.removeSubscriber(debugLogEmitter);
      debugLogEmitter.complete();
      log.debug("SSE 로그 스트리밍 타임아웃 (5분)");
    });
    debugLogEmitter.onError(throwable -> {
      sseLogBroadcaster.removeSubscriber(debugLogEmitter);
      log.debug("SSE 로그 스트리밍 에러: {}", throwable.getMessage());
    });

    log.info("SSE 로그 스트리밍 연결 수립 (활성 구독자: {}명)", sseLogBroadcaster.getActiveSubscriberCount());

    return debugLogEmitter;
  }
}
```

- [ ] **Step 2: 커밋**

```
테스트_빌드용_SSE_서버_로그_스트리밍_엔드포인트_추가 : feat : DebugController SSE 로그 스트리밍 엔드포인트 구현 https://github.com/TEAM-ROMROM/RomRom-BE/issues/607
```

---

## Task 8: 통합 검증

- [ ] **Step 1: 파일 구조 최종 확인**

생성/수정된 파일 목록:
```
RomRom-Common/src/main/java/com/romrom/common/dto/DebugLogEvent.java          (신규)
RomRom-Common/src/main/java/com/romrom/common/service/SseLogBroadcaster.java  (신규)
RomRom-Common/src/main/java/com/romrom/common/logging/SseLogAppender.java     (신규)
RomRom-Common/src/main/java/com/romrom/common/logging/SseLogAppenderInitializer.java (신규)
RomRom-Web/src/main/resources/logback-spring.xml                               (신규)
RomRom-Web/src/main/java/com/romrom/web/controller/api/DebugControllerDocs.java (신규)
RomRom-Web/src/main/java/com/romrom/web/controller/api/DebugController.java    (신규)
RomRom-Domain-Auth/src/main/java/com/romrom/auth/dto/SecurityUrls.java         (수정)
```

- [ ] **Step 2: import 및 의존성 확인**

확인 사항:
- `SseEmitter`는 `spring-web`에 포함 → 별도 의존성 불필요
- `ch.qos.logback.classic.spi.ILoggingEvent`는 `logback-classic`에 포함 → Spring Boot 기본 포함
- `com.fasterxml.jackson.datatype.jsr310.JavaTimeModule`은 `jackson-datatype-jsr310`에 포함 → Spring Boot 기본 포함
- `@SecuredApi`, `SseLogBroadcaster`는 RomRom-Common 모듈 → RomRom-Web이 의존하므로 접근 가능

- [ ] **Step 3: logback-spring.xml과 application.yml 충돌 확인**

`logback-spring.xml`이 존재하면 Spring Boot는 `application.yml`의 `logging.level.*` 설정을 여전히 적용하지만, `logging.file.name` 등 일부 설정은 `logback-spring.xml`에서 `springProperty`로 가져와야 한다. 위 설정에서 이미 `springProperty`로 `LOG_FILE`을 가져오고 있으므로 충돌 없음.

- [ ] **Step 4: 수동 테스트 방법 메모**

서버 기동 후 curl로 테스트:
```bash
# HMAC 서명 생성 (bash)
TIMESTAMP=$(date +%s%3N)
SIGNATURE=$(echo -n "$TIMESTAMP" | openssl dgst -sha256 -hmac "SECRET_KEY_HERE" | awk '{print $2}')

# SSE 스트림 연결
curl -N -H "X-Timestamp: $TIMESTAMP" -H "X-Signature: $SIGNATURE" \
  http://localhost:8080/api/app/debug/log-stream
```

예상 출력:
```
data: {"timestamp":"2026-03-27T14:30:00.123","level":"INFO","loggerName":"com.romrom.web.controller.api.DebugController","message":"SSE 로그 스트리밍 연결 수립 (활성 구독자: 1명)","threadName":"http-nio-8080-exec-1"}

data: {"timestamp":"2026-03-27T14:30:01.456","level":"DEBUG","loggerName":"com.romrom.common.service.SseLogBroadcaster","message":"...","threadName":"http-nio-8080-exec-2"}
```

- [ ] **Step 5: 최종 커밋**

```
테스트_빌드용_SSE_서버_로그_스트리밍_엔드포인트_추가 : docs : 설계 및 구현 계획 문서 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/607
```
