# 접속·방문자 실시간 분석 구현 계획 (#772)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 모든 사용자 행동을 단일 이벤트 스트림(MongoDB)으로 수집하고, Redis 기반 실시간 온라인 집계 + 범용 시계열 집계 API + Admin 대시보드 차트를 구현한다.

**Architecture:** RomRom-Common에 공통 수집 인프라(`UserActivityEvent` 문서, `ActivityEventRecorder` 비동기 적재, `OnlineUserTracker` Redis ZSET)를 둔다. RomRom-Web의 `HandlerInterceptor`가 모든 요청에서 이벤트를 기록하고 온라인 상태를 갱신한다. RomRom-Application의 `AdminAnalyticsService`가 집계하고 `AdminApiController`가 2개 API로 노출하며, Admin dashboard에 ApexCharts로 시각화한다.

**Tech Stack:** Spring Boot, MongoDB 4.4 (일반 컬렉션 + TTL 인덱스, Time Series 미사용), Redis (ZSET), @Async, Thymeleaf + ApexCharts

> **운영 제약:** 운영 MongoDB가 4.4.29라 Time Series Collection(5.0+) 불가. 일반 `@Document` 컬렉션 + `occurredAt` TTL 인덱스로 구현.
> **테스트 정책:** 이 프로젝트는 무거운 `@SpringBootTest`를 대부분 비활성(주석)화하고 빌드 통과 + 수동 testcase로 검증한다. 본 계획은 순수 로직(집계 유틸 등)만 가벼운 단위테스트로 검증하고, 인프라 의존 코드는 **빌드 컴파일 통과**를 검증 기준으로 삼는다.

빌드 명령: `./gradlew :RomRom-Web:compileJava -q` (전체 모듈 의존 컴파일). 전체 빌드: `./gradlew build -x test -q`.

---

## File Structure

**RomRom-Common (공통 수집 인프라)**
- Create: `RomRom-Common/src/main/java/com/romrom/common/entity/mongo/UserActivityEvent.java` — 단일 이벤트 문서
- Create: `RomRom-Common/src/main/java/com/romrom/common/entity/mongo/EventType.java` — 이벤트 종류 enum (category 보유)
- Create: `RomRom-Common/src/main/java/com/romrom/common/entity/mongo/EventCategory.java` — 대분류 enum
- Create: `RomRom-Common/src/main/java/com/romrom/common/repository/UserActivityEventRepository.java` — Mongo repository
- Create: `RomRom-Common/src/main/java/com/romrom/common/service/ActivityEventRecorder.java` — 단일 비동기 기록 진입점
- Create: `RomRom-Common/src/main/java/com/romrom/common/service/OnlineUserTracker.java` — Redis ZSET 온라인 추적
- Create: `RomRom-Common/src/main/java/com/romrom/common/config/ActivityAsyncConfig.java` — 적재 전용 Executor 빈

**RomRom-Web (수집 지점)**
- Create: `RomRom-Web/src/main/java/com/romrom/web/interceptor/ActivityTrackingInterceptor.java`
- Modify: `RomRom-Web/src/main/java/com/romrom/web/config/WebConfig.java` — 인터셉터 등록

**RomRom-Application (집계/API/스케줄러)**
- Create: `RomRom-Application/src/main/java/com/romrom/application/service/AdminAnalyticsService.java`
- Create: `RomRom-Application/src/main/java/com/romrom/application/scheduler/ConcurrentUserSnapshotScheduler.java`
- Modify: `RomRom-Application/src/main/java/com/romrom/application/dto/AdminRequest.java` — 필드 추가
- Modify: `RomRom-Application/src/main/java/com/romrom/application/dto/AdminResponse.java` — 내부 DTO + 필드 추가
- Modify: `RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiController.java` — 2개 엔드포인트
- Modify: `RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiControllerDocs.java` (있으면) 또는 Operation/@ApiChangeLog

**Frontend**
- Modify: `RomRom-Web/src/main/resources/templates/admin/layout.html` — ApexCharts CDN
- Modify: `RomRom-Web/src/main/resources/templates/admin/dashboard.html` — 실시간 카드 + 차트

---

## Task 1: 이벤트 분류 enum 정의

**Files:**
- Create: `RomRom-Common/src/main/java/com/romrom/common/entity/mongo/EventCategory.java`
- Create: `RomRom-Common/src/main/java/com/romrom/common/entity/mongo/EventType.java`

- [ ] **Step 1: EventCategory enum 작성**

```java
package com.romrom.common.entity.mongo;

/**
 * 이벤트 대분류 — 집계 그룹핑용.
 * 새 분류가 필요하면 값만 추가한다 (DB 스키마 변경 없음).
 */
public enum EventCategory {
  TRAFFIC,      // 방문/접속 (PAGE_VIEW, API_CALL, SESSION_*)
  ENGAGEMENT,   // 사용자 참여 (ITEM_VIEW, SEARCH, CLICK)
  TRANSACTION,  // 거래 (TRADE_CREATED 등)
  SYSTEM        // 시스템 스냅샷 (CONCURRENT_USERS)
}
```

- [ ] **Step 2: EventType enum 작성 (각 타입이 소속 category 보유)**

```java
package com.romrom.common.entity.mongo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이벤트 종류 — 새 지표는 이 enum에 값 하나만 추가하면 된다.
 * 컬렉션/인덱스/마이그레이션 불변. (공통 이벤트 스트림 설계)
 */
@Getter
@RequiredArgsConstructor
public enum EventType {
  // 트래픽
  PAGE_VIEW(EventCategory.TRAFFIC),
  API_CALL(EventCategory.TRAFFIC),
  SESSION_START(EventCategory.TRAFFIC),
  SESSION_END(EventCategory.TRAFFIC),
  // 시스템 스냅샷 (스케줄러가 현재 온라인 수를 주기 적재)
  CONCURRENT_USERS(EventCategory.SYSTEM);
  // 확장 예시: ITEM_VIEW(EventCategory.ENGAGEMENT), SEARCH(EventCategory.ENGAGEMENT) ...

  private final EventCategory category;
}
```

- [ ] **Step 3: 컴파일 검증**

Run: `./gradlew :RomRom-Common:compileJava -q`
Expected: BUILD SUCCESSFUL (에러 없음)

- [ ] **Step 4: 커밋**

```bash
git add RomRom-Common/src/main/java/com/romrom/common/entity/mongo/EventCategory.java RomRom-Common/src/main/java/com/romrom/common/entity/mongo/EventType.java
git commit -m "접속_방문자_실시간_분석 : feat : 공통 이벤트 분류 EventType/EventCategory enum 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/772"
```

---

## Task 2: UserActivityEvent 문서 + Repository

**Files:**
- Create: `RomRom-Common/src/main/java/com/romrom/common/entity/mongo/UserActivityEvent.java`
- Create: `RomRom-Common/src/main/java/com/romrom/common/repository/UserActivityEventRepository.java`

- [ ] **Step 1: UserActivityEvent 문서 작성**

`BaseMongoEntity`를 상속하지 않는다 — 본 문서는 `occurredAt`을 직접 timeField로 쓰고 TTL을 건다. (BaseMongoEntity의 createdDate/updatedDate는 불필요한 중복)

```java
package com.romrom.common.entity.mongo;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 공통 사용자 행동 이벤트 — 모든 지표가 이 단일 스트림에서 집계로 파생된다. (#772)
 * 운영 Mongo 4.4라 Time Series 미사용. 일반 컬렉션 + occurredAt TTL 인덱스로 90일 자동 만료.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "user_activity_event")
@CompoundIndex(name = "idx_type_time", def = "{'eventType': 1, 'occurredAt': -1}")
@CompoundIndex(name = "idx_category_time", def = "{'eventCategory': 1, 'occurredAt': -1}")
public class UserActivityEvent {

  @Id
  private String eventId; // UUID 문자열

  private EventType eventType;
  private EventCategory eventCategory;

  private UUID memberId;       // 비로그인은 null
  private String anonymousId;  // 비로그인 식별 (쿠키)
  private String sessionId;    // 세션 집계용

  private String path;
  private String ip;
  private String userAgent;
  private String platform;     // AOS / iOS / WEB

  // raw 이벤트 90일 후 자동 만료 (TTL 인덱스). 단위: 초
  @Indexed(name = "ttl_occurred_at", expireAfterSeconds = 60 * 60 * 24 * 90)
  private Instant occurredAt;

  // 이벤트별 자유 확장 필드 — 새 지표는 여기에 키를 담는다 (스키마 변경 없음)
  @Builder.Default
  private Map<String, Object> properties = new HashMap<>();

  public static UserActivityEvent of(EventType eventType, UUID memberId, String anonymousId,
      String sessionId, String path, String ip, String userAgent, String platform,
      Map<String, Object> properties) {
    return UserActivityEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType(eventType)
        .eventCategory(eventType.getCategory())
        .memberId(memberId)
        .anonymousId(anonymousId)
        .sessionId(sessionId)
        .path(path)
        .ip(ip)
        .userAgent(userAgent)
        .platform(platform)
        .occurredAt(Instant.now())
        .properties(properties != null ? properties : new HashMap<>())
        .build();
  }
}
```

- [ ] **Step 2: Repository 작성**

```java
package com.romrom.common.repository;

import com.romrom.common.entity.mongo.UserActivityEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserActivityEventRepository extends MongoRepository<UserActivityEvent, String> {
}
```

집계 쿼리는 `MongoTemplate` aggregation으로 `AdminAnalyticsService`에서 직접 수행한다 (Task 7). 단순 count류는 추후 메서드 추가.

- [ ] **Step 3: 컴파일 검증**

Run: `./gradlew :RomRom-Common:compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add RomRom-Common/src/main/java/com/romrom/common/entity/mongo/UserActivityEvent.java RomRom-Common/src/main/java/com/romrom/common/repository/UserActivityEventRepository.java
git commit -m "접속_방문자_실시간_분석 : feat : UserActivityEvent 문서 + Repository 추가 (TTL 90일 인덱스) https://github.com/TEAM-ROMROM/RomRom-BE/issues/772"
```

---

## Task 3: 적재 전용 Async Executor 설정

**Files:**
- Create: `RomRom-Common/src/main/java/com/romrom/common/config/ActivityAsyncConfig.java`

- [ ] **Step 1: Executor 빈 작성**

적재 실패가 본 요청에 영향 주지 않도록 전용 풀 + 큐 포화 시 폐기(DiscardPolicy) 정책 사용.

```java
package com.romrom.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 사용자 행동 이벤트 적재 전용 스레드풀 (#772)
 * - 본 요청 스레드를 막지 않도록 분리
 * - 큐 포화 시 이벤트를 폐기(Discard)해 본 요청 지연/실패를 방지 (이벤트 유실 허용)
 */
@Configuration
public class ActivityAsyncConfig {

  public static final String ACTIVITY_EVENT_EXECUTOR = "activityEventExecutor";

  @Bean(name = ACTIVITY_EVENT_EXECUTOR)
  public Executor activityEventExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("activity-event-");
    // 큐가 가득 차면 새 이벤트를 버린다 (적재보다 본 요청 보호 우선)
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
    executor.initialize();
    return executor;
  }
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `./gradlew :RomRom-Common:compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add RomRom-Common/src/main/java/com/romrom/common/config/ActivityAsyncConfig.java
git commit -m "접속_방문자_실시간_분석 : feat : 이벤트 적재 전용 Async Executor 설정 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/772"
```

---

## Task 4: OnlineUserTracker (Redis ZSET)

**Files:**
- Create: `RomRom-Common/src/main/java/com/romrom/common/service/OnlineUserTracker.java`

ZSET 구조: key=`online:users`, member=식별자(memberId 또는 anonymousId), score=만료 epoch milli.
- `touch`: score를 (now + TTL)로 ZADD → 매 요청마다 만료시각 갱신
- `countOnline`: score >= now 인 멤버 수 (ZCOUNT)
- `cleanup`: score < now 인 멤버 제거 (ZREMRANGEBYSCORE) — 스케줄러가 주기 호출

- [ ] **Step 1: 구현 작성**

```java
package com.romrom.common.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 현재 온라인 사용자 추적 — Redis ZSET (#772)
 * score=만료 epoch(ms). 매 API 요청마다 touch로 만료시각을 미래로 갱신,
 * 만료(score < now)된 멤버는 count에서 제외되고 cleanup으로 정리된다.
 * 누적 DB가 아니라 실시간 set이므로 정확하다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnlineUserTracker {

  private static final String ONLINE_USERS_KEY = "online:users";
  // 마지막 활동 후 이 시간이 지나면 오프라인으로 간주 (ms)
  private static final long ONLINE_TTL_MILLIS = 5 * 60 * 1000L;

  private final RedisTemplate<String, Object> redisTemplate;

  /** 요청 시 호출 — 식별자의 온라인 만료시각을 (now + TTL)로 갱신 */
  public void touch(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      return;
    }
    long expireAt = Instant.now().toEpochMilli() + ONLINE_TTL_MILLIS;
    try {
      redisTemplate.opsForZSet().add(ONLINE_USERS_KEY, identifier, expireAt);
    } catch (Exception e) {
      // 온라인 추적 실패는 본 요청에 영향 주지 않는다
      log.warn("온라인 사용자 touch 실패: identifier={}, error={}", identifier, e.getMessage());
    }
  }

  /** 현재 온라인 수 = 만료시각이 아직 안 지난 멤버 수 */
  public long countOnline() {
    long now = Instant.now().toEpochMilli();
    try {
      Long count = redisTemplate.opsForZSet().count(ONLINE_USERS_KEY, now, Double.POSITIVE_INFINITY);
      return count != null ? count : 0L;
    } catch (Exception e) {
      log.warn("온라인 사용자 count 실패: error={}", e.getMessage());
      return 0L;
    }
  }

  /** 만료된 멤버 정리 — 스케줄러가 주기 호출 (ZSET 무한 증가 방지) */
  public void cleanupExpired() {
    long now = Instant.now().toEpochMilli();
    try {
      Long removed = redisTemplate.opsForZSet()
          .removeRangeByScore(ONLINE_USERS_KEY, Double.NEGATIVE_INFINITY, now - 1);
      if (removed != null && removed > 0) {
        log.debug("만료된 온라인 사용자 {}명 정리", removed);
      }
    } catch (Exception e) {
      log.warn("온라인 사용자 cleanup 실패: error={}", e.getMessage());
    }
  }
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `./gradlew :RomRom-Common:compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add RomRom-Common/src/main/java/com/romrom/common/service/OnlineUserTracker.java
git commit -m "접속_방문자_실시간_분석 : feat : Redis ZSET 기반 OnlineUserTracker 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/772"
```

---

## Task 5: ActivityEventRecorder (단일 비동기 기록 진입점)

**Files:**
- Create: `RomRom-Common/src/main/java/com/romrom/common/service/ActivityEventRecorder.java`

- [ ] **Step 1: 구현 작성**

```java
package com.romrom.common.service;

import com.romrom.common.entity.mongo.EventType;
import com.romrom.common.entity.mongo.UserActivityEvent;
import com.romrom.common.repository.UserActivityEventRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static com.romrom.common.config.ActivityAsyncConfig.ACTIVITY_EVENT_EXECUTOR;

/**
 * 모든 사용자 행동 이벤트의 단일 기록 진입점 (#772)
 * - 모든 수집 지점(Interceptor, 도메인 서비스 등)은 이 메서드만 호출한다
 * - @Async 전용 풀로 비동기 적재 — 본 요청 지연 방지, 적재 실패가 본 요청에 전파되지 않음
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityEventRecorder {

  private final UserActivityEventRepository userActivityEventRepository;

  @Async(ACTIVITY_EVENT_EXECUTOR)
  public void record(EventType eventType, UUID memberId, String anonymousId, String sessionId,
      String path, String ip, String userAgent, String platform, Map<String, Object> properties) {
    try {
      UserActivityEvent event = UserActivityEvent.of(
          eventType, memberId, anonymousId, sessionId, path, ip, userAgent, platform, properties);
      userActivityEventRepository.save(event);
    } catch (Exception e) {
      // 적재 실패는 본 요청과 무관하게 로그만 남긴다 (유실 허용)
      log.warn("사용자 행동 이벤트 적재 실패: eventType={}, error={}", eventType, e.getMessage());
    }
  }
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `./gradlew :RomRom-Common:compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add RomRom-Common/src/main/java/com/romrom/common/service/ActivityEventRecorder.java
git commit -m "접속_방문자_실시간_분석 : feat : 단일 비동기 기록 진입점 ActivityEventRecorder 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/772"
```

---

## Task 6: ActivityTrackingInterceptor + WebConfig 등록

**Files:**
- Create: `RomRom-Web/src/main/java/com/romrom/web/interceptor/ActivityTrackingInterceptor.java`
- Modify: `RomRom-Web/src/main/java/com/romrom/web/config/WebConfig.java`

식별 전략:
- 로그인: `SecurityContextHolder`의 principal에서 memberId (가능하면), 실패 시 null
- 비로그인: `anonymousId` 쿠키 — 없으면 발급(UUID)해서 응답 쿠키로 set
- platform: `X-Platform` 헤더(AOS/iOS) 우선, 없으면 User-Agent로 WEB 추정

> 주의: SecurityContext에서 memberId를 안전하게 꺼내는 방식은 기존 코드(`CustomUserDetails`)를 따른다. principal이 `CustomUserDetails`면 `getMember().getMemberId()`, 아니면 null.

- [ ] **Step 1: Interceptor 작성**

```java
package com.romrom.web.interceptor;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.entity.mongo.EventType;
import com.romrom.common.service.ActivityEventRecorder;
import com.romrom.common.service.OnlineUserTracker;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 모든 HTTP 요청에서 방문 이벤트(PAGE_VIEW/API_CALL)를 기록하고 온라인 상태를 갱신한다. (#772)
 * 화이트리스트(정적/헬스/admin/swagger)는 WebConfig.excludePathPatterns로 제외.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityTrackingInterceptor implements HandlerInterceptor {

  private static final String ANONYMOUS_COOKIE = "anonymousId";
  private static final int ANONYMOUS_COOKIE_MAX_AGE = 60 * 60 * 24 * 365; // 1년

  private final ActivityEventRecorder activityEventRecorder;
  private final OnlineUserTracker onlineUserTracker;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    try {
      UUID memberId = resolveMemberId();
      String anonymousId = resolveOrIssueAnonymousId(request, response);
      // 온라인 식별자: 로그인 우선, 없으면 익명
      String onlineIdentifier = memberId != null ? memberId.toString() : anonymousId;
      onlineUserTracker.touch(onlineIdentifier);

      // API 호출이면 API_CALL, 그 외 페이지는 PAGE_VIEW
      String path = request.getRequestURI();
      EventType eventType = path.startsWith("/api/") ? EventType.API_CALL : EventType.PAGE_VIEW;

      Map<String, Object> properties = new HashMap<>();
      properties.put("method", request.getMethod());

      activityEventRecorder.record(
          eventType, memberId, anonymousId, request.getSession(false) != null ? request.getSession().getId() : null,
          path, resolveClientIp(request), request.getHeader("User-Agent"),
          resolvePlatform(request), properties);
    } catch (Exception e) {
      // 추적 실패는 절대 본 요청을 막지 않는다
      log.warn("활동 추적 인터셉터 처리 실패: {}", e.getMessage());
    }
    return true;
  }

  /** SecurityContext에서 memberId 추출 (CustomUserDetails일 때만) */
  private UUID resolveMemberId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
      return userDetails.getMember().getMemberId();
    }
    return null;
  }

  /** anonymousId 쿠키 조회, 없으면 발급해서 응답에 set */
  private String resolveOrIssueAnonymousId(HttpServletRequest request, HttpServletResponse response) {
    if (request.getCookies() != null) {
      for (Cookie cookie : request.getCookies()) {
        if (ANONYMOUS_COOKIE.equals(cookie.getName())) {
          return cookie.getValue();
        }
      }
    }
    String newAnonymousId = UUID.randomUUID().toString();
    Cookie cookie = new Cookie(ANONYMOUS_COOKIE, newAnonymousId);
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setMaxAge(ANONYMOUS_COOKIE_MAX_AGE);
    response.addCookie(cookie);
    return newAnonymousId;
  }

  /** 프록시 환경 고려한 클라이언트 IP */
  private String resolveClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  /** 플랫폼 식별: X-Platform 헤더 우선, 없으면 WEB */
  private String resolvePlatform(HttpServletRequest request) {
    String platform = request.getHeader("X-Platform");
    return (platform != null && !platform.isBlank()) ? platform : "WEB";
  }
}
```

> **검증 필요:** `CustomUserDetails`에 `getMember().getMemberId()`가 있는지, 또는 `getMemberId()` 직접 메서드가 있는지 실제 코드로 확인 후 맞춘다 (CustomChannelInterceptor에서 `customUserDetails.getMemberId()` 호출을 본 기억 — 실제 시그니처 확인).

- [ ] **Step 2: CustomUserDetails 시그니처 확인**

Run: `grep -n "getMemberId\|getMember" RomRom-Domain-Auth/src/main/java/com/romrom/auth/dto/CustomUserDetails.java`
Expected: `getMemberId()` 또는 `getMember()` 존재 확인 → Step 1 코드의 `resolveMemberId()`를 실제 시그니처에 맞춘다.

- [ ] **Step 3: WebConfig에 인터셉터 등록**

기존 `WebConfig.java`의 `addResourceHandlers`는 유지하고 `addInterceptors`를 추가한다. 생성자 주입을 위해 `@RequiredArgsConstructor` 추가.

```java
package com.romrom.web.config;

import com.romrom.web.interceptor.ActivityTrackingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ActivityTrackingInterceptor activityTrackingInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/");
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/assets/");
        registry.addResourceHandler("/plugins/**").addResourceLocations("classpath:/static/plugins/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 활동 추적 인터셉터 — 정적/헬스/admin/swagger는 화이트리스트로 제외 (#772)
        registry.addInterceptor(activityTrackingInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/css/**", "/js/**", "/assets/**", "/plugins/**",  // 정적 리소스
                        "/actuator/**", "/health", "/favicon.ico",          // 헬스/시스템
                        "/api/admin/**",                                     // admin 자체 호출 제외
                        "/swagger-ui/**", "/v3/api-docs/**",                 // swagger
                        "/error"
                );
    }
}
```

- [ ] **Step 4: 컴파일 검증**

Run: `./gradlew :RomRom-Web:compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add RomRom-Web/src/main/java/com/romrom/web/interceptor/ActivityTrackingInterceptor.java RomRom-Web/src/main/java/com/romrom/web/config/WebConfig.java
git commit -m "접속_방문자_실시간_분석 : feat : 활동 추적 인터셉터 추가 및 WebConfig 등록 (화이트리스트 제외) https://github.com/TEAM-ROMROM/RomRom-BE/issues/772"
```

---

## Task 7: AdminAnalyticsService (집계)

**Files:**
- Create: `RomRom-Application/src/main/java/com/romrom/application/service/AdminAnalyticsService.java`

집계 두 가지:
1. `getRealtime()` — 현재 온라인(Redis) + 오늘 PV/UV(Mongo aggregation) + 오늘 신규가입/신규거래(기존 도메인 repo)
2. `getTimeseries(eventType, eventCategory, from, to, bucketUnit)` — Mongo aggregation, 시간/일 버킷 그룹핑

> **검증 필요:** 오늘 신규가입/신규거래 count는 기존 `AdminDashboardService`가 이미 계산한다 (`todayNewMembers` 등). 중복 구현 대신 기존 repository 메서드 재사용 — 실제 메서드명을 `AdminDashboardService`에서 확인해 호출한다.

- [ ] **Step 1: AdminDashboardService의 오늘자 집계 메서드 확인**

Run: `grep -n "today\|countBy.*Date\|Today" RomRom-Application/src/main/java/com/romrom/application/service/AdminDashboardService.java`
Expected: 오늘 신규 회원/거래 카운트 메서드/repository 호출 확인 → AdminAnalyticsService에서 동일 repository 재사용.

- [ ] **Step 2: AdminAnalyticsService 작성**

PV = 오늘 PAGE_VIEW + API_CALL 이벤트 수. UV = 오늘 distinct (memberId 또는 anonymousId).
시계열은 `occurredAt`을 KST 기준 버킷으로 그룹핑.

```java
package com.romrom.application.service;

import com.romrom.application.dto.AdminResponse;
import com.romrom.common.entity.mongo.EventCategory;
import com.romrom.common.entity.mongo.EventType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * 관리자 실시간 분석 집계 서비스 (#772)
 * - 현재 온라인: Redis (OnlineUserTracker)
 * - 오늘 PV/UV, 시계열: MongoDB user_activity_event aggregation
 * - 오늘 신규가입/신규거래: 기존 도메인 repository 재사용
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAnalyticsService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String COLLECTION = "user_activity_event";

  private final com.romrom.common.service.OnlineUserTracker onlineUserTracker;
  private final MongoTemplate mongoTemplate;
  // 오늘 신규가입/신규거래 재사용 (Step 1에서 확인한 실제 의존성으로 교체)
  private final AdminDashboardService adminDashboardService;

  /** 실시간 카드용 집계 */
  public AdminResponse getRealtime() {
    long onlineCount = onlineUserTracker.countOnline();

    Instant todayStart = LocalDate.now(KST).atStartOfDay(KST).toInstant();
    Instant now = Instant.now();

    long todayPv = countEvents(todayStart, now, null, EventCategory.TRAFFIC);
    long todayUv = countDistinctVisitors(todayStart, now);

    // 오늘 신규가입/신규거래 — 기존 대시보드 통계 재사용 (Step 1 확인 결과로 교체)
    AdminResponse.AdminDashboardStats stats = adminDashboardService.getDashboardStats(null);

    AdminResponse.AdminAnalyticsRealtime realtime = AdminResponse.AdminAnalyticsRealtime.builder()
        .onlineCount(onlineCount)
        .todayPv(todayPv)
        .todayUv(todayUv)
        .todayNewMembers(stats != null ? stats.getTodayNewMembers() : 0L)
        .todayNewTrades(0L) // Step 1에서 오늘 거래 카운트 메서드 확인 후 채움
        .build();

    return AdminResponse.builder().analyticsRealtime(realtime).build();
  }

  /** 범용 시계열 — eventType 또는 eventCategory + 기간 + 버킷(HOUR/DAY) */
  public AdminResponse getTimeseries(EventType eventType, EventCategory eventCategory,
      Instant from, Instant to, String bucketUnit) {

    // 버킷 포맷: HOUR -> 시간단위, DAY -> 일단위 (KST)
    String dateFormat = "DAY".equalsIgnoreCase(bucketUnit) ? "%Y-%m-%d" : "%Y-%m-%dT%H:00";

    List<Document> pipeline = new ArrayList<>();

    Document match = new Document("occurredAt", new Document("$gte", from).append("$lte", to));
    if (eventType != null) {
      match.append("eventType", eventType.name());
    } else if (eventCategory != null) {
      match.append("eventCategory", eventCategory.name());
    }
    pipeline.add(new Document("$match", match));

    Document dateToString = new Document("format", dateFormat)
        .append("date", "$occurredAt")
        .append("timezone", "Asia/Seoul");
    pipeline.add(new Document("$group", new Document("_id",
        new Document("$dateToString", dateToString)).append("count", new Document("$sum", 1))));
    pipeline.add(new Document("$sort", new Document("_id", 1)));

    List<AdminResponse.AdminAnalyticsTimeBucket> buckets = new ArrayList<>();
    mongoTemplate.getCollection(COLLECTION)
        .aggregate(pipeline)
        .forEach(doc -> buckets.add(AdminResponse.AdminAnalyticsTimeBucket.builder()
            .bucketTime(doc.getString("_id"))
            .count(((Number) doc.get("count")).longValue())
            .build()));

    return AdminResponse.builder().analyticsTimeseries(buckets).build();
  }

  /** 기간 내 이벤트 수 (eventType 또는 category 필터) */
  private long countEvents(Instant from, Instant to, EventType eventType, EventCategory eventCategory) {
    Document filter = new Document("occurredAt", new Document("$gte", from).append("$lte", to));
    if (eventType != null) {
      filter.append("eventType", eventType.name());
    } else if (eventCategory != null) {
      filter.append("eventCategory", eventCategory.name());
    }
    return mongoTemplate.getCollection(COLLECTION).countDocuments(filter);
  }

  /** 기간 내 고유 방문자 수 = distinct(memberId ?? anonymousId) */
  private long countDistinctVisitors(Instant from, Instant to) {
    List<Document> pipeline = List.of(
        new Document("$match", new Document("occurredAt",
            new Document("$gte", from).append("$lte", to))),
        new Document("$group", new Document("_id",
            new Document("$ifNull", List.of("$memberId", "$anonymousId")))),
        new Document("$count", "uv"));
    Document result = mongoTemplate.getCollection(COLLECTION).aggregate(pipeline).first();
    return result != null ? ((Number) result.get("uv")).longValue() : 0L;
  }
}
```

> Step 1에서 확인한 결과에 따라 `adminDashboardService.getDashboardStats(null)` 호출 시그니처와 오늘 거래 카운트를 정확히 맞춘다. `getDashboardStats`가 인자를 받는 형태가 다르면 해당 메서드의 실제 시그니처로 교체한다.

- [ ] **Step 3: 컴파일 검증** (AdminResponse 내부 DTO는 Task 8에서 추가 — 이 Task는 Task 8과 함께 컴파일됨. 순서상 Task 8을 먼저 해도 무방하나, 여기서는 Task 8 완료 후 함께 검증)

Run: `./gradlew :RomRom-Application:compileJava -q`
Expected: (Task 8 완료 전이면) AdminAnalyticsRealtime/AdminAnalyticsTimeBucket 미정의 에러 → Task 8 먼저 적용 후 재검증. 둘 다 적용되면 BUILD SUCCESSFUL.

- [ ] **Step 4: 커밋** (Task 8과 묶어서 커밋 권장 — 상호 의존)

---

## Task 8: AdminRequest / AdminResponse 필드 추가

**Files:**
- Modify: `RomRom-Application/src/main/java/com/romrom/application/dto/AdminRequest.java`
- Modify: `RomRom-Application/src/main/java/com/romrom/application/dto/AdminResponse.java`

- [ ] **Step 1: AdminRequest에 분석 필드 추가**

`AdminRequest.java`의 마지막 필드(`imageUploadParallelPoolSize`) 뒤, 클래스 닫는 `}` 직전에 추가:

```java
    // 실시간 분석 관련 필드 (#772)
    @Schema(description = "시계열 집계 대상 이벤트 종류 (PAGE_VIEW/API_CALL/CONCURRENT_USERS 등). eventCategory와 택일")
    private com.romrom.common.entity.mongo.EventType analyticsEventType;

    @Schema(description = "시계열 집계 대상 이벤트 대분류 (TRAFFIC/ENGAGEMENT 등). eventType 미지정 시 사용")
    private com.romrom.common.entity.mongo.EventCategory analyticsEventCategory;

    @Schema(description = "시계열 버킷 단위 (HOUR=시간별, DAY=일별)", defaultValue = "HOUR")
    private String bucketUnit;

    @Schema(description = "분석 기간 시작 (ISO-8601 Instant, 예: 2026-06-01T00:00:00Z). 미지정 시 최근 24시간)")
    private String analyticsFrom;

    @Schema(description = "분석 기간 종료 (ISO-8601 Instant). 미지정 시 현재)")
    private String analyticsTo;
```

> `startDate`/`endDate`는 yyyy-MM-dd 문자열이라 기존 의미와 충돌하므로, 분석용은 별도 `analyticsFrom`/`analyticsTo`(Instant 문자열)로 둔다.

- [ ] **Step 2: AdminResponse에 분석 필드 + 내부 DTO 추가**

`AdminResponse.java`의 공통 페이징 필드(`currentPage`) 뒤, 첫 내부 static class(`AdminMemberDetailDto`) 앞에 필드 추가:

```java
    // 실시간 분석 응답 데이터 (#772)
    @Schema(description = "실시간 카드 집계 (현재 온라인/오늘 UV·PV/신규가입·신규거래)")
    private AdminAnalyticsRealtime analyticsRealtime;

    @Schema(description = "시계열 집계 결과 (버킷별 count)")
    private List<AdminAnalyticsTimeBucket> analyticsTimeseries;
```

그리고 마지막 내부 static class(`AdminDashboardStats`) 뒤, 클래스 닫는 `}` 직전에 내부 DTO 2개 추가:

```java
    @ToString
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @Schema(description = "실시간 분석 카드 집계 (#772)")
    public static class AdminAnalyticsRealtime {

        @Schema(description = "현재 온라인 사용자 수 (Redis, 최근 5분 내 활동)")
        private Long onlineCount;

        @Schema(description = "오늘 페이지뷰 (PV) - TRAFFIC 이벤트 수")
        private Long todayPv;

        @Schema(description = "오늘 순방문자 (UV) - distinct member/anonymous")
        private Long todayUv;

        @Schema(description = "오늘 신규 가입 회원 수")
        private Long todayNewMembers;

        @Schema(description = "오늘 신규 거래(생성) 건수")
        private Long todayNewTrades;
    }

    @ToString
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @Schema(description = "시계열 버킷 (#772)")
    public static class AdminAnalyticsTimeBucket {

        @Schema(description = "버킷 시각 (HOUR: yyyy-MM-dd'T'HH:00, DAY: yyyy-MM-dd, KST)")
        private String bucketTime;

        @Schema(description = "해당 버킷 이벤트 수")
        private Long count;
    }
```

- [ ] **Step 3: 컴파일 검증 (Task 7 + 8 합산)**

Run: `./gradlew :RomRom-Application:compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋 (Task 7 + 8)**

```bash
git add RomRom-Application/src/main/java/com/romrom/application/dto/AdminRequest.java RomRom-Application/src/main/java/com/romrom/application/dto/AdminResponse.java RomRom-Application/src/main/java/com/romrom/application/service/AdminAnalyticsService.java
git commit -m "접속_방문자_실시간_분석 : feat : AdminAnalyticsService 집계 + AdminRequest/Response 분석 필드 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/772"
```

---

## Task 9: 동접 스냅샷 스케줄러

**Files:**
- Create: `RomRom-Application/src/main/java/com/romrom/application/scheduler/ConcurrentUserSnapshotScheduler.java`

N분 주기로 현재 온라인 수를 `CONCURRENT_USERS` 이벤트로 적재(시간별 동접 추이 데이터화) + 만료 멤버 cleanup.

- [ ] **Step 1: 스케줄러 작성**

```java
package com.romrom.application.scheduler;

import com.romrom.common.entity.mongo.EventType;
import com.romrom.common.service.ActivityEventRecorder;
import com.romrom.common.service.OnlineUserTracker;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 현재 온라인 수 스냅샷 스케줄러 (#772)
 * - 5분 주기로 현재 온라인 수를 CONCURRENT_USERS 이벤트로 적재 → 시간별 동접 추이 데이터
 * - 만료된 온라인 멤버 정리(ZSET 무한 증가 방지)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConcurrentUserSnapshotScheduler {

  private final OnlineUserTracker onlineUserTracker;
  private final ActivityEventRecorder activityEventRecorder;

  // 5분 주기 (cron: 매시 0,5,10,...분 0초)
  @Scheduled(cron = "0 */5 * * * *")
  public void snapshotConcurrentUsers() {
    onlineUserTracker.cleanupExpired();
    long onlineCount = onlineUserTracker.countOnline();

    Map<String, Object> properties = new HashMap<>();
    properties.put("onlineCount", onlineCount);

    // 시스템 스냅샷 이벤트 — memberId/anonymousId 등은 null
    activityEventRecorder.record(
        EventType.CONCURRENT_USERS, null, null, null, null, null, null, "SYSTEM", properties);

    log.debug("동접 스냅샷 적재: onlineCount={}", onlineCount);
  }
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `./gradlew :RomRom-Application:compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add RomRom-Application/src/main/java/com/romrom/application/scheduler/ConcurrentUserSnapshotScheduler.java
git commit -m "접속_방문자_실시간_분석 : feat : 동접 스냅샷 스케줄러 추가 (5분 주기) https://github.com/TEAM-ROMROM/RomRom-BE/issues/772"
```

---

## Task 10: AdminApiController 엔드포인트 + Docs

**Files:**
- Modify: `RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiController.java`
- Modify: Docs 인터페이스 (Step 1에서 존재 확인) 또는 컨트롤러 내 `@Operation`/`@ApiChangeLog`

- [ ] **Step 1: AdminApiController가 Docs 인터페이스를 구현하는지 확인**

Run: `grep -n "implements\|class AdminApiController" RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiController.java; ls RomRom-Web/src/main/java/com/romrom/web/controller/api/ | grep -i AdminApiControllerDocs`
Expected: Docs 인터페이스 존재 여부 확인. 있으면 거기에 `@Operation`+`@ApiChangeLog` 작성, 없으면 컨트롤러 메서드에 직접 작성(기존 dashboard 메서드 방식 따름).

- [ ] **Step 2: 컨트롤러에 의존성 + 2개 엔드포인트 추가**

필드 주입부에 추가:
```java
    private final com.romrom.application.service.AdminAnalyticsService adminAnalyticsService;
```

`getDashboardStats` 등 dashboard 메서드 근처에 추가 (Instant 파싱: 미지정 시 최근 24시간 기본값):
```java
    @PostMapping(value = "/analytics/realtime", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminResponse> getAnalyticsRealtime(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAnalyticsService.getRealtime());
    }

    @PostMapping(value = "/analytics/timeseries", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminResponse> getAnalyticsTimeseries(@ModelAttribute AdminRequest request) {
        java.time.Instant to = (request.getAnalyticsTo() != null && !request.getAnalyticsTo().isBlank())
                ? java.time.Instant.parse(request.getAnalyticsTo())
                : java.time.Instant.now();
        java.time.Instant from = (request.getAnalyticsFrom() != null && !request.getAnalyticsFrom().isBlank())
                ? java.time.Instant.parse(request.getAnalyticsFrom())
                : to.minus(java.time.Duration.ofHours(24));
        String bucketUnit = (request.getBucketUnit() != null && !request.getBucketUnit().isBlank())
                ? request.getBucketUnit() : "HOUR";
        return ResponseEntity.ok(adminAnalyticsService.getTimeseries(
                request.getAnalyticsEventType(), request.getAnalyticsEventCategory(), from, to, bucketUnit));
    }
```

- [ ] **Step 3: @ApiChangeLog 최상단 추가**

Docs 인터페이스(또는 컨트롤러)의 `@ApiChangeLogs` 배열 최상단에 추가 (Author enum의 실제 값은 기존 항목 참고해 맞춤):
```java
    @ApiChangeLog(
        date = "2026.06.05",
        author = Author.SUHSAECHAN,
        issueNumber = 772,
        description = "접속·방문자 실시간 분석 API 추가 (/analytics/realtime, /analytics/timeseries)"
    ),
```

- [ ] **Step 4: 컴파일 검증**

Run: `./gradlew :RomRom-Web:compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiController.java
git commit -m "접속_방문자_실시간_분석 : feat : Admin 실시간 분석 API 2종 추가 (realtime/timeseries) + ApiChangeLog https://github.com/TEAM-ROMROM/RomRom-BE/issues/772"
```

---

## Task 11: Admin dashboard 프론트 (ApexCharts)

**Files:**
- Modify: `RomRom-Web/src/main/resources/templates/admin/layout.html`
- Modify: `RomRom-Web/src/main/resources/templates/admin/dashboard.html`

- [ ] **Step 1: dashboard.html / layout.html 현재 구조 파악**

Run: `grep -n "script\|cdn\|chart\|dashboard\|<head\|</body" RomRom-Web/src/main/resources/templates/admin/layout.html | head -30; echo "---"; grep -n "stat\|card\|row\|col\|api/admin/dashboard" RomRom-Web/src/main/resources/templates/admin/dashboard.html | head -40`
Expected: layout의 script include 위치와 dashboard의 카드/그리드 구조 파악 → 기존 디자인 클래스(daisyUI/Tailwind 또는 AdminLTE 등)를 그대로 따라 카드/차트 삽입.

- [ ] **Step 2: layout.html에 ApexCharts CDN 추가**

`</body>` 직전 또는 기존 공통 script include 영역에 추가:
```html
<!-- ApexCharts (실시간 분석 차트, #772) -->
<script src="https://cdn.jsdelivr.net/npm/apexcharts@3.49.1"></script>
```

- [ ] **Step 3: dashboard.html에 실시간 카드 + 차트 섹션 추가**

기존 통계 카드 영역 아래(또는 적절한 위치)에 추가. **기존 템플릿의 그리드/카드 클래스를 Step 1에서 확인한 것과 동일하게 사용한다.** 아래는 구조 예시 — 실제 클래스명은 기존 dashboard.html에 맞춘다:

```html
<!-- 실시간 분석 섹션 (#772) -->
<div class="row mt-4">
  <div class="col-12"><h4>실시간 분석</h4></div>
</div>
<div class="row" id="realtime-cards">
  <div class="col-md-3"><div class="card"><div class="card-body">
    <div class="text-muted">🟢 현재 온라인</div>
    <h3 id="card-online">-</h3>
  </div></div></div>
  <div class="col-md-3"><div class="card"><div class="card-body">
    <div class="text-muted">오늘 방문자 (UV)</div>
    <h3 id="card-uv">-</h3>
  </div></div></div>
  <div class="col-md-3"><div class="card"><div class="card-body">
    <div class="text-muted">오늘 페이지뷰 (PV)</div>
    <h3 id="card-pv">-</h3>
  </div></div></div>
  <div class="col-md-3"><div class="card"><div class="card-body">
    <div class="text-muted">오늘 신규가입</div>
    <h3 id="card-new-members">-</h3>
  </div></div></div>
</div>

<div class="row mt-4">
  <div class="col-md-6"><div class="card"><div class="card-body">
    <h5>시간별 동시접속자 추이</h5>
    <div id="chart-concurrent"></div>
  </div></div></div>
  <div class="col-md-6"><div class="card"><div class="card-body">
    <h5>일별 방문자 추이</h5>
    <div id="chart-daily-visitors"></div>
  </div></div></div>
</div>
```

- [ ] **Step 4: dashboard.html에 JS 추가 (폴링 + 차트 렌더)**

dashboard.html 하단 script 영역에 추가. **요청은 기존 admin API 호출 방식(fetch + multipart FormData, credentials 포함)을 Step 1에서 확인한 패턴대로 따른다.**

```html
<script>
(function () {
  // 공통 admin API 호출 (multipart, 쿠키 인증)
  async function adminPost(url, params) {
    const formData = new FormData();
    if (params) Object.entries(params).forEach(([k, v]) => formData.append(k, v));
    const res = await fetch(url, { method: 'POST', body: formData, credentials: 'same-origin' });
    if (!res.ok) throw new Error('요청 실패: ' + res.status);
    return res.json();
  }

  // 실시간 카드 갱신
  async function refreshRealtime() {
    try {
      const data = await adminPost('/api/admin/analytics/realtime');
      const r = data.analyticsRealtime || {};
      document.getElementById('card-online').textContent = r.onlineCount ?? 0;
      document.getElementById('card-uv').textContent = r.todayUv ?? 0;
      document.getElementById('card-pv').textContent = r.todayPv ?? 0;
      document.getElementById('card-new-members').textContent = r.todayNewMembers ?? 0;
    } catch (e) { console.warn('realtime 갱신 실패', e); }
  }

  // 시계열 차트 렌더
  let concurrentChart, dailyChart;
  function renderChart(elementId, title, categories, series, existing) {
    const options = {
      chart: { type: 'area', height: 280, toolbar: { show: true }, zoom: { enabled: true } },
      series: [{ name: title, data: series }],
      xaxis: { categories: categories },
      dataLabels: { enabled: false },
      stroke: { curve: 'smooth', width: 2 },
      tooltip: { x: { show: true } }
    };
    if (existing) { existing.updateOptions(options); return existing; }
    const chart = new ApexCharts(document.querySelector('#' + elementId), options);
    chart.render();
    return chart;
  }

  async function refreshCharts() {
    try {
      // 시간별 동접 (CONCURRENT_USERS, HOUR 버킷, 최근 24h)
      const concurrent = await adminPost('/api/admin/analytics/timeseries',
        { analyticsEventType: 'CONCURRENT_USERS', bucketUnit: 'HOUR' });
      const cBuckets = concurrent.analyticsTimeseries || [];
      concurrentChart = renderChart('chart-concurrent', '동시접속자',
        cBuckets.map(b => b.bucketTime), cBuckets.map(b => b.count), concurrentChart);

      // 일별 방문자 (API_CALL 또는 TRAFFIC category, DAY 버킷, 최근 14일)
      const to = new Date();
      const from = new Date(Date.now() - 14 * 24 * 60 * 60 * 1000);
      const daily = await adminPost('/api/admin/analytics/timeseries',
        { analyticsEventCategory: 'TRAFFIC', bucketUnit: 'DAY',
          analyticsFrom: from.toISOString(), analyticsTo: to.toISOString() });
      const dBuckets = daily.analyticsTimeseries || [];
      dailyChart = renderChart('chart-daily-visitors', '방문자',
        dBuckets.map(b => b.bucketTime), dBuckets.map(b => b.count), dailyChart);
    } catch (e) { console.warn('charts 갱신 실패', e); }
  }

  // 최초 1회 + 폴링
  document.addEventListener('DOMContentLoaded', function () {
    refreshRealtime();
    refreshCharts();
    setInterval(refreshRealtime, 30 * 1000); // 30초마다 실시간 카드 갱신
    setInterval(refreshCharts, 5 * 60 * 1000); // 5분마다 차트 갱신
  });
})();
</script>
```

- [ ] **Step 5: 빌드 검증 (템플릿은 컴파일 대상 아님 — 전체 빌드로 깨짐 없는지)**

Run: `./gradlew :RomRom-Web:compileJava -q`
Expected: BUILD SUCCESSFUL (자바 변경 없으므로 통과)

- [ ] **Step 6: 커밋**

```bash
git add RomRom-Web/src/main/resources/templates/admin/layout.html RomRom-Web/src/main/resources/templates/admin/dashboard.html
git commit -m "접속_방문자_실시간_분석 : feat : Admin 대시보드 실시간 카드 + ApexCharts 동접/방문자 추이 차트 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/772"
```

---

## Task 12: 전체 빌드 검증 + 컬렉션 인덱스 확인

**Files:** 없음 (검증 전용)

- [ ] **Step 1: 전체 빌드 (테스트 제외)**

Run: `./gradlew build -x test -q`
Expected: BUILD SUCCESSFUL — 모든 모듈 컴파일 통과

- [ ] **Step 2: 애플리케이션 기동 시 TTL/Compound 인덱스 자동 생성 확인 (수동/로컬)**

Spring Data Mongo는 `@Indexed`/`@CompoundIndex`를 기본적으로 자동 생성한다. `application.yml`에 `spring.data.mongodb.auto-index-creation` 설정이 있는지 확인:
Run: `grep -rn "auto-index-creation" RomRom-Web/src/main/resources/`
- 설정이 `false`거나 없으면(기본값 환경따라 다름) → `application.yml`에 `spring.data.mongodb.auto-index-creation: true` 추가하거나, 기존 Mongo 엔티티(`ChatMessage`)가 인덱스 생성되는 방식을 따른다.

> 기존 `ChatMessage`도 `@CompoundIndex`를 쓰므로 동일 메커니즘으로 생성될 것. 다른 점은 본 문서의 TTL 인덱스(`@Indexed expireAfterSeconds`)뿐 — 자동 생성 설정이 켜져 있어야 TTL이 실제 적용된다.

- [ ] **Step 3: (검증만, 커밋 없음)** 빌드 통과 확인 후 다음 단계(수동 testcase/배포)로 진행

---

## 후속 단계 (이 계획 범위 밖, CLAUDE.md 작업 흐름)

1. `/suh-report` + `/suh-testcase`로 보고서/테스트케이스 작성 → 이슈 #772 댓글 포스팅 (`@suh-lab server build` 태그)
2. `/cassiiopeia:suh-github`로 이슈 라벨 `작업전` → `작업완료` 변경
3. `/cassiiopeia:suh-changelog-deploy`로 deploy PR 생성 및 배포

---

## Self-Review 체크 결과

- **Spec 커버리지:** UserActivityEvent(T2)/EventType·Category(T1)/Recorder(T5)/Async(T3)/OnlineTracker(T4)/Interceptor(T6)/Scheduler(T9)/AdminAnalyticsService(T7)/API 2종(T10)/Request·Response(T8)/Frontend(T11) — spec 9개 컴포넌트 전부 태스크 존재. ✅
- **운영 제약:** Time Series 미사용 + 일반 컬렉션 TTL → T2에 반영. ✅
- **온라인 = Redis ZSET:** T4에 반영. ✅
- **확장성:** 새 eventType = enum 1줄 + record 호출 → T1/T5 구조로 충족. ✅
- **컨벤션:** 단일 AdminRequest/Response(T8), POST+multipart(T10), Admin Service는 Application(T7), Common entity/repo 위치(T1·T2), Docs+ApiChangeLog(T10). ✅
- **검증 필요 항목 명시:** CustomUserDetails 시그니처(T6 Step2), AdminDashboardService 오늘자 메서드(T7 Step1), Docs 인터페이스 존재(T10 Step1), 템플릿 클래스/fetch 패턴(T11 Step1), auto-index-creation(T12 Step2) — 각 태스크에 확인 스텝으로 포함. ✅
- **타입 일관성:** `AdminAnalyticsRealtime`/`AdminAnalyticsTimeBucket`(T7 사용 ↔ T8 정의), `ACTIVITY_EVENT_EXECUTOR`(T3 정의 ↔ T5 사용), `OnlineUserTracker.touch/countOnline/cleanupExpired`(T4 정의 ↔ T6·T9 사용), `EventType.getCategory()`(T1 정의 ↔ T2 사용) 일치. ✅
