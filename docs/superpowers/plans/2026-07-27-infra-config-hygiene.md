# 설정/인프라 위생 및 측정 기반 확보 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 코드 변경 없이 설정만으로 JPA 배치 페치·커넥션 풀·로그레벨을 정상화하고, 이후 B/C/D 단계의 개선 효과를 측정할 Prometheus 메트릭 기반을 확보한다.

**Architecture:** 모든 변경은 `application.yml`(공통), `application-dev.yml`, `logback-spring.xml`, `RomRom-Web/build.gradle`에 국한된다. 운영 프로필은 GitHub Secret `APPLICATION_PROD_YML`에서 생성되므로 Secret 갱신이 별도 태스크로 존재한다. 회귀 방지는 **인프라 없이 도는 YAML 구조 테스트**로 잡는다 — 이번 버그(`open-in-view` 키 위치 오류)가 정확히 이 계층에서 발생했기 때문이다.

**Tech Stack:** Spring Boot 3.4.1, Java 17, Gradle 멀티모듈, HikariCP, Micrometer/Prometheus, SnakeYAML 2.3(테스트), JUnit 5

## Global Constraints

- **`spring.jpa.hibernate.ddl-auto: update`와 `spring.jpa.generate-ddl: true`는 변경하지 않는다.** 의도된 운영 설계다(Spring이 처리 가능한 스키마 변경은 Hibernate, 나머지는 Flyway).
- **`spring.flyway.validate-on-migrate: false`는 유지한다.** 켜면 체크섬이 깨진 마이그레이션에서 배포가 즉시 중단된다.
- **`open-in-view`는 `true`로 명시한다.** `false` 전환은 미보호 LAZY 연관관계 25개 감사가 선행돼야 하며 서브프로젝트 D 범위다.
- **`/actuator/health`는 `SecurityUrls.AUTH_WHITELIST`에 반드시 유지한다.** `Dockerfile`의 HEALTHCHECK와 Traefik 블루/그린 전환이 이 경로에 의존한다.
- 커밋 메시지에 AI 서명·`Co-Authored-By`·`@mention`을 넣지 않는다.
- 커밋은 `git add -A` 금지. 해당 태스크가 건드린 경로만 명시해 스테이징한다.
- Java 파일 주석은 한국어로, WHY 중심으로 간결하게 작성한다.
- 테스트 메서드명은 기존 관례대로 한국어 스네이크 표기를 쓴다 (예: `단일라인_정상포맷_파싱()`).

## File Structure

| 파일 | 책임 | 작업 |
|---|---|---|
| `RomRom-Web/src/main/resources/application.yml` | 공통 설정 — JPA 튜닝, HikariCP, actuator, Flyway | 수정 |
| `RomRom-Web/src/main/resources/application-dev.yml` | 개발 프로필 — SQL 로깅 | 수정 |
| `RomRom-Web/src/main/resources/logback-spring.xml` | Appender 정의만 담당 (로그레벨은 yml로 이관) | 수정 |
| `RomRom-Web/build.gradle` | micrometer-registry-prometheus 의존성 | 수정 |
| `RomRom-Web/src/test/java/com/romrom/web/config/ApplicationYamlStructureTest.java` | 설정 키 위치·존재 회귀 방지 (Spring 컨텍스트·DB 불필요) | 생성 |
| `RomRom-Web/src/test/java/com/romrom/web/config/ConfigBindingTest.java` | 런타임 바인딩 검증 (로컬 인프라 필요) | 생성 |
| GitHub Secret `APPLICATION_PROD_YML` | 운영 프로필 실제 소스 | 갱신 |

`ApplicationYamlStructureTest`와 `ConfigBindingTest`를 분리하는 이유: 전자는 인프라 없이 CI/로컬 어디서나 돌아 회귀를 항상 잡고, 후자는 로컬 DB·Redis가 있을 때만 도는 통합 검증이다. 하나로 합치면 인프라 없는 환경에서 회귀 방지 자체가 죽는다.

---

## Task 0: 이슈 생성

**Files:** 없음 (GitHub 작업)

**Interfaces:**
- Produces: 이슈 번호 `{ISSUE}` — 이후 모든 태스크의 커밋 메시지와 브랜치명에 사용

- [ ] **Step 1: `/pro-github` 스킬로 이슈 생성**

제목: `[기능개선] 설정/인프라 위생 및 측정 기반 확보 (서브프로젝트 A)`

본문에 포함할 내용:
- 설계 문서 링크: `docs/superpowers/specs/2026-07-27-infra-config-hygiene-design.md`
- 실측된 문제 7건 요약 (spec의 "실측 근거" 표)
- 범위 밖 항목 명시 (`ddl-auto` 미변경, OSIV 전환은 D)

라벨을 `작업중`으로 설정한다.

- [ ] **Step 2: 작업 브랜치 생성**

`/pro-init-worktree` 스킬로 worktree를 만든다. 브랜치명 예: `20260727_#{ISSUE}_기능개선_설정_인프라_위생_및_측정_기반_확보`

`main`에 직접 커밋하지 않는다.

---

## Task 1: YAML 구조 회귀 방지 테스트 골격

이번 버그의 본질은 "키가 잘못된 위치에 있어 조용히 무시됨"이다. 이걸 잡는 테스트를 **설정을 고치기 전에** 먼저 만들고, 현재 상태에서 실패하는 것을 확인한다.

**Files:**
- Test: `RomRom-Web/src/test/java/com/romrom/web/config/ApplicationYamlStructureTest.java` (생성)

**Interfaces:**
- Consumes: 없음
- Produces: `ApplicationYamlStructureTest` — Task 2~5가 이 클래스에 테스트 메서드를 추가한다.
  헬퍼 시그니처: `private Object valueAt(String... yamlKeyPath)` — 중첩 맵을 경로로 탐색해 값 반환, 경로 중간이 끊기면 `null`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.romrom.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * application.yml의 설정 키 위치·존재 여부를 검증한다.
 *
 * Spring 컨텍스트를 띄우지 않으므로 DB·Redis 없이 어디서나 실행된다.
 * 과거 open-in-view 키가 spring.jpa.properties.hibernate 아래에 있어
 * 조용히 무시된 이력이 있어, 키 위치 자체를 회귀 대상으로 삼는다.
 */
class ApplicationYamlStructureTest {

  private Map<String, Object> applicationYamlRoot;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void loadApplicationYaml() throws Exception {
    try (InputStream applicationYamlStream =
        getClass().getResourceAsStream("/application.yml")) {
      assertNotNull(applicationYamlStream, "application.yml을 클래스패스에서 찾지 못했다");
      applicationYamlRoot = new Yaml().loadAs(applicationYamlStream, Map.class);
    }
  }

  @Test
  @DisplayName("open-in-view는 spring.jpa 직속에 있어야 Spring Boot가 인식한다")
  void openInView가_spring_jpa_직속에_존재한다() {
    assertNotNull(valueAt("spring", "jpa", "open-in-view"));
  }

  @Test
  @DisplayName("open-in-view가 hibernate properties 아래에 있으면 무시된다")
  void openInView가_hibernate_properties_아래에_없다() {
    assertNull(valueAt("spring", "jpa", "properties", "hibernate", "open-in-view"));
  }

  /**
   * 중첩 맵을 키 경로로 탐색한다. 경로가 중간에 끊기면 null을 반환해
   * "키가 없다"와 "값이 null이다"를 동일하게 취급한다 — 설정 검증 목적상 둘은 같다.
   */
  @SuppressWarnings("unchecked")
  private Object valueAt(String... yamlKeyPath) {
    Object currentNode = applicationYamlRoot;
    for (String yamlKey : yamlKeyPath) {
      if (!(currentNode instanceof Map)) {
        return null;
      }
      currentNode = ((Map<String, Object>) currentNode).get(yamlKey);
    }
    return currentNode;
  }
}
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인한다**

Run: `./gradlew :RomRom-Web:test --tests "com.romrom.web.config.ApplicationYamlStructureTest"`

Expected: `openInView가_spring_jpa_직속에_존재한다` FAIL (현재 `spring.jpa.open-in-view` 키가 없음),
`openInView가_hibernate_properties_아래에_없다` FAIL (현재 잘못된 위치에 존재함)

두 테스트가 **모두 실패해야 한다.** 하나만 실패하면 현재 상태 파악이 틀린 것이므로 멈추고 `application.yml`을 다시 확인한다.

- [ ] **Step 3: 커밋**

```bash
git add RomRom-Web/src/test/java/com/romrom/web/config/ApplicationYamlStructureTest.java
git commit -m "설정_인프라_위생_및_측정_기반_확보 : test : application.yml 키 위치 회귀 방지 테스트 추가(#{ISSUE})"
```

---

## Task 2: JPA/Hibernate 설정 교정

**Files:**
- Modify: `RomRom-Web/src/main/resources/application.yml:19-31` (spring.jpa 블록)
- Test: `RomRom-Web/src/test/java/com/romrom/web/config/ApplicationYamlStructureTest.java` (메서드 추가)

**Interfaces:**
- Consumes: Task 1의 `ApplicationYamlStructureTest`, `valueAt(String...)`
- Produces: `spring.jpa.properties.hibernate.default_batch_fetch_size = 100` — Task 7의 배포 후 실측이 이 값의 효과를 측정한다

- [ ] **Step 1: 실패하는 테스트 추가**

`ApplicationYamlStructureTest`에 메서드를 추가한다.

```java
  @Test
  @DisplayName("show_sql은 stdout 직행이라 운영에서 차단 불가 — 제거되어야 한다")
  void showSql_관련_설정이_모두_제거되었다() {
    assertNull(valueAt("spring", "jpa", "properties", "hibernate", "show_sql"));
    assertNull(valueAt("spring", "jpa", "properties", "hibernate", "format_sql"));
    assertNull(valueAt("spring", "jpa", "properties", "hibernate", "use_sql_comments"));
  }

  @Test
  @DisplayName("배치 페치 사이즈가 N+1을 IN 절로 접는 핵심 설정")
  void 배치_페치_사이즈가_설정되어_있다() {
    assertEquals(100, valueAt("spring", "jpa", "properties", "hibernate", "default_batch_fetch_size"));
    assertEquals(50, valueAt("spring", "jpa", "properties", "hibernate", "jdbc", "batch_size"));
    assertEquals(100, valueAt("spring", "jpa", "properties", "hibernate", "jdbc", "fetch_size"));
  }

  @Test
  @DisplayName("ddl-auto와 generate-ddl은 의도된 운영 설계 — 변경 금지")
  void 스키마_생성_설정은_기존_값을_유지한다() {
    assertEquals("update", valueAt("spring", "jpa", "hibernate", "ddl-auto"));
    assertEquals(true, valueAt("spring", "jpa", "generate-ddl"));
  }
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인한다**

Run: `./gradlew :RomRom-Web:test --tests "com.romrom.web.config.ApplicationYamlStructureTest"`

Expected: `showSql_관련_설정이_모두_제거되었다` FAIL, `배치_페치_사이즈가_설정되어_있다` FAIL,
`스키마_생성_설정은_기존_값을_유지한다` PASS (기존 값이 이미 맞으므로 — 이건 변경 금지 가드다)

- [ ] **Step 3: application.yml의 spring.jpa 블록을 교체한다**

기존 블록:

```yaml
  jpa:
    generate-ddl: true
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        show_sql: true
        format_sql: true
        open-in-view: false
        use_sql_comments: true
```

교체 후:

```yaml
  jpa:
    # 현재 실제 동작값을 명시한다. 기존에는 이 키가 properties.hibernate 아래에 있어
    # Spring Boot가 인식하지 못했고 기본값 true로 동작 중이었다.
    # false 전환은 미보호 LAZY 연관관계 감사가 선행돼야 해 서브프로젝트 D에서 처리한다.
    open-in-view: true
    generate-ddl: true
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        # 지연 로딩 N+1을 IN 절 배치 조회로 접는다
        default_batch_fetch_size: 100
        jdbc:
          batch_size: 50
          fetch_size: 100
        order_inserts: true
        order_updates: true
        # IN 절 파라미터 개수를 2의 거듭제곱으로 패딩해 쿼리 플랜 캐시 히트율을 높인다
        query:
          in_clause_parameter_padding: true
```

`show_sql` / `format_sql` / `use_sql_comments`는 삭제한다. SQL 로깅은 Task 4에서 logback 경유로 대체한다.

- [ ] **Step 4: 테스트를 돌려 통과를 확인한다**

Run: `./gradlew :RomRom-Web:test --tests "com.romrom.web.config.ApplicationYamlStructureTest"`

Expected: 5개 테스트 전부 PASS

- [ ] **Step 5: 커밋**

```bash
git add RomRom-Web/src/main/resources/application.yml \
        RomRom-Web/src/test/java/com/romrom/web/config/ApplicationYamlStructureTest.java
git commit -m "설정_인프라_위생_및_측정_기반_확보 : fix : open-in-view 키 위치 교정 및 JPA 배치 페치 튜닝(#{ISSUE})"
```

---

## Task 3: HikariCP 커넥션 풀 설정

**Files:**
- Modify: `RomRom-Web/src/main/resources/application.yml` (spring.datasource 추가)
- Test: `RomRom-Web/src/test/java/com/romrom/web/config/ApplicationYamlStructureTest.java` (메서드 추가)

**Interfaces:**
- Consumes: Task 1의 `valueAt(String...)`
- Produces: `RomRomHikariPool` 풀 이름 — Task 7의 배포 후 실측에서 설정 반영 여부를 이 이름으로 판별한다

프로필별 값이 동일하므로 공통 `application.yml`에 둔다. `application-prod.yml`은 GitHub Secret에서 생성되므로 여기에 두어야 운영까지 자동 반영된다. Spring은 `spring.datasource.url`(프로필별)과 `spring.datasource.hikari.*`(공통)를 키 단위로 병합하므로 충돌하지 않는다.

- [ ] **Step 1: 실패하는 테스트 추가**

```java
  @Test
  @DisplayName("커넥션 풀 설정이 없으면 HikariCP 기본값 10으로 동작한다")
  void 커넥션_풀_설정이_존재한다() {
    assertEquals("RomRomHikariPool", valueAt("spring", "datasource", "hikari", "pool-name"));
    assertEquals(20, valueAt("spring", "datasource", "hikari", "maximum-pool-size"));
    assertEquals(5, valueAt("spring", "datasource", "hikari", "minimum-idle"));
  }

  @Test
  @DisplayName("누수 탐지는 D의 OSIV 전환에 필요한 커넥션 보유 시간 근거를 수집한다")
  void 커넥션_누수_탐지가_켜져_있다() {
    assertEquals(5000, valueAt("spring", "datasource", "hikari", "leak-detection-threshold"));
  }
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인한다**

Run: `./gradlew :RomRom-Web:test --tests "com.romrom.web.config.ApplicationYamlStructureTest"`

Expected: 두 테스트 FAIL (`spring.datasource` 자체가 application.yml에 없음)

- [ ] **Step 3: application.yml에 datasource 블록을 추가한다**

`spring:` 아래, `flyway:` 블록 바로 앞에 삽입한다. 접속 정보(url/username/password)는 프로필별 파일에 그대로 두고 여기엔 풀 설정만 둔다.

```yaml
  datasource:
    hikari:
      pool-name: RomRomHikariPool
      # 블루/그린 배포로 두 인스턴스가 동시 기동되는 구간이 있어 실제 최대는 20 x 2 = 40이다.
      # PostgreSQL max_connections(기본 100) 내에서 다른 클라이언트 여유를 남긴다.
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      # 5초 이상 반환되지 않는 커넥션을 경고로 남긴다 (OSIV 전환 판단 근거 수집)
      leak-detection-threshold: 5000
```

- [ ] **Step 4: 테스트를 돌려 통과를 확인한다**

Run: `./gradlew :RomRom-Web:test --tests "com.romrom.web.config.ApplicationYamlStructureTest"`

Expected: 7개 테스트 전부 PASS

- [ ] **Step 5: 커밋**

```bash
git add RomRom-Web/src/main/resources/application.yml \
        RomRom-Web/src/test/java/com/romrom/web/config/ApplicationYamlStructureTest.java
git commit -m "설정_인프라_위생_및_측정_기반_확보 : feat : HikariCP 커넥션 풀 설정 및 누수 탐지 추가(#{ISSUE})"
```

---

## Task 4: SQL 로깅을 logback 경유로 전환 + logback 로그레벨 정리

`show_sql`은 stdout 직행이라 로그레벨로 제어할 수 없다. Hibernate의 SQL 로거를 쓰면 logback을 타므로 프로필별 제어가 가능해진다.

또한 로그레벨이 `logback-spring.xml`과 각 프로필 yml에 이중 정의돼 있어, 어느 쪽이 이겼는지 추적이 어렵다. logback은 Appender 정의만 담당하도록 정리한다.

**Files:**
- Modify: `RomRom-Web/src/main/resources/application-dev.yml` (logging.level 추가)
- Modify: `RomRom-Web/src/main/resources/logback-spring.xml:29-38` (logger 엘리먼트 제거)

**Interfaces:**
- Consumes: Task 2에서 `show_sql`이 제거된 상태
- Produces: 없음

`application-dev.yml`은 `.gitignore` 대상이라 커밋되지 않는다. 로컬 파일을 직접 수정하고, 팀 공유가 필요하면 별도 이슈로 처리한다.

- [ ] **Step 1: application-dev.yml의 logging.level에 Hibernate SQL 로거를 추가한다**

기존 `logging.level` 블록 안, `com.romrom: DEBUG` 아래에 추가한다.

```yaml
    org:
      hibernate:
        SQL: DEBUG
        orm:
          jdbc:
            bind: TRACE
      springframework: WARN
      springframework.web.servlet.DispatcherServlet: WARN
      apache:
        catalina: WARN
      springdoc: WARN
      springframework.boot.autoconfigure.logging: OFF
```

주의: 기존 블록에 있던 `hibernate: WARN`(`org.hibernate` 전체를 WARN으로 낮추는 설정)은 제거한다. 남겨두면 `org.hibernate.SQL: DEBUG`와 충돌하지 않지만(더 구체적인 로거가 이김) 의도가 불분명해진다.

`application-prod.yml`과 `application-test.yml`에는 **추가하지 않는다** — 미설정이 곧 출력 없음이다.

- [ ] **Step 2: logback-spring.xml에서 로그레벨 정의를 제거한다**

아래 블록 전체를 삭제한다.

```xml
  <!-- 프레임워크 로그 레벨 (기존 application.yml 설정 유지, application.yml의 logging.level.*이 추가로 오버라이드 가능) -->
  <logger name="org.springframework" level="WARN"/>
  <logger name="org.springframework.web.servlet.DispatcherServlet" level="WARN"/>
  <logger name="org.hibernate" level="WARN"/>
  <logger name="org.springdoc" level="WARN"/>
  <logger name="org.apache.catalina" level="WARN"/>
  <logger name="org.springframework.boot.autoconfigure.logging" level="OFF"/>

  <!-- suh-logger 라이브러리 -->
  <logger name="me.suhsaechan.suh-logger" level="DEBUG"/>
  <logger name="me.suhsaechan.suh-api-log" level="DEBUG"/>

  <!-- 애플리케이션 로그 -->
  <logger name="com.romrom" level="DEBUG"/>
```

대신 그 자리에 주석을 남긴다.

```xml
  <!-- 로그레벨은 application-{profile}.yml의 logging.level에서 단일 관리한다.
       여기서는 Appender 정의만 담당한다. -->
```

- [ ] **Step 3: dev 프로필로 기동해 SQL 로그가 logback 포맷으로 나오는지 확인한다**

로컬 PostgreSQL·Redis·MongoDB가 필요하다.

Run: `./gradlew :RomRom-Web:bootRun --args='--spring.profiles.active=dev'`

Expected:
- SQL이 `2026-07-27 ... DEBUG org.hibernate.SQL - select ...` 형태로 출력된다 (타임스탬프·로거명이 붙은 logback 포맷)
- 기존 `show_sql`의 포맷 없는 raw SQL 출력이 사라진다

로컬 인프라가 없으면 이 단계를 건너뛰고 Task 7의 배포 후 실측에서 확인한다. **건너뛴 경우 그 사실을 기록한다.**

- [ ] **Step 4: 전체 테스트를 돌려 회귀가 없는지 확인한다**

Run: `./gradlew :RomRom-Web:test --tests "com.romrom.web.config.ApplicationYamlStructureTest"`

Expected: 7개 PASS (이 태스크는 application.yml을 건드리지 않으므로 영향 없음을 확인하는 목적)

- [ ] **Step 5: 커밋**

`application-dev.yml`은 gitignore 대상이므로 스테이징되지 않는다. logback만 커밋한다.

```bash
git add RomRom-Web/src/main/resources/logback-spring.xml
git commit -m "설정_인프라_위생_및_측정_기반_확보 : refactor : 로그레벨 정의를 yml로 단일화(#{ISSUE})"
```

---

## Task 5: Prometheus 메트릭 노출

**Files:**
- Modify: `RomRom-Web/build.gradle:23` 부근 (actuator 의존성 아래)
- Modify: `RomRom-Web/src/main/resources/application.yml` (management 블록)
- Test: `RomRom-Web/src/test/java/com/romrom/web/config/ApplicationYamlStructureTest.java` (메서드 추가)

**Interfaces:**
- Consumes: Task 1의 `valueAt(String...)`
- Produces: `/actuator/prometheus` 엔드포인트 — Task 7의 실측이 이 경로에서 지표를 수집한다

- [ ] **Step 1: 실패하는 테스트 추가**

```java
  @Test
  @DisplayName("메트릭 노출이 없으면 B/C/D의 개선 효과를 측정할 수단이 없다")
  void actuator에_prometheus가_노출된다() {
    Object exposureInclude = valueAt("management", "endpoints", "web", "exposure", "include");
    assertNotNull(exposureInclude);
    String exposureIncludeText = exposureInclude.toString();
    org.junit.jupiter.api.Assertions.assertTrue(
        exposureIncludeText.contains("prometheus"),
        "prometheus가 노출 목록에 없다: " + exposureIncludeText);
    org.junit.jupiter.api.Assertions.assertTrue(
        exposureIncludeText.contains("health"),
        "health가 빠지면 Dockerfile HEALTHCHECK와 Traefik 블루/그린 전환이 깨진다: " + exposureIncludeText);
  }
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인한다**

Run: `./gradlew :RomRom-Web:test --tests "com.romrom.web.config.ApplicationYamlStructureTest"`

Expected: `actuator에_prometheus가_노출된다` FAIL — "prometheus가 노출 목록에 없다: health"

- [ ] **Step 3: build.gradle에 micrometer 의존성을 추가한다**

`RomRom-Web/build.gradle`의 actuator 줄 바로 아래에 추가한다.

```gradle
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
```

버전은 명시하지 않는다 — Spring Boot BOM이 관리한다.

- [ ] **Step 4: application.yml의 management 블록을 교체한다**

기존:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
```

교체 후:

```yaml
management:
  endpoints:
    web:
      exposure:
        # health는 Dockerfile HEALTHCHECK와 Traefik 블루/그린 전환이 의존하므로 반드시 유지한다.
        # prometheus/metrics는 SecurityUrls.AUTH_WHITELIST에 없어 JWT 필터로 자동 보호된다.
        include: health,metrics,prometheus
  metrics:
    tags:
      application: romrom
```

- [ ] **Step 5: 테스트를 돌려 통과를 확인한다**

Run: `./gradlew :RomRom-Web:test --tests "com.romrom.web.config.ApplicationYamlStructureTest"`

Expected: 8개 테스트 전부 PASS

- [ ] **Step 6: 컴파일이 깨지지 않는지 확인한다**

Run: `./gradlew :RomRom-Web:compileJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add RomRom-Web/build.gradle \
        RomRom-Web/src/main/resources/application.yml \
        RomRom-Web/src/test/java/com/romrom/web/config/ApplicationYamlStructureTest.java
git commit -m "설정_인프라_위생_및_측정_기반_확보 : feat : Prometheus 메트릭 노출 추가(#{ISSUE})"
```

---

## Task 6: Flyway 죽은 설정 제거

`spring.flyway.repair-on-migrate`는 Spring Boot 3.4.1의 설정 메타데이터에 존재하지 않는다. 주석은 "마이그레이션 전 자동 repair"라고 설명하나 실제로는 무시돼 왔다. 동작을 바꾸지 않고 죽은 설정만 제거해, 다음 사람이 없는 안전장치를 믿지 않게 한다.

**Files:**
- Modify: `RomRom-Web/src/main/resources/application.yml:11-17` (flyway 블록)
- Test: `RomRom-Web/src/test/java/com/romrom/web/config/ApplicationYamlStructureTest.java` (메서드 추가)

**Interfaces:**
- Consumes: Task 1의 `valueAt(String...)`
- Produces: 없음

- [ ] **Step 1: 실패하는 테스트 추가**

```java
  @Test
  @DisplayName("repair-on-migrate는 Spring Boot 3.4.1에 없는 속성 — 있으면 안전장치를 오해하게 된다")
  void 존재하지_않는_flyway_속성이_없다() {
    assertNull(valueAt("spring", "flyway", "repair-on-migrate"));
  }

  @Test
  @DisplayName("validate-on-migrate를 켜면 체크섬 깨진 마이그레이션에서 배포가 중단된다 — 유지")
  void flyway_검증_설정은_기존_값을_유지한다() {
    assertEquals(false, valueAt("spring", "flyway", "validate-on-migrate"));
    assertEquals(true, valueAt("spring", "flyway", "baseline-on-migrate"));
    assertEquals(false, valueAt("spring", "flyway", "out-of-order"));
  }
```

- [ ] **Step 2: 테스트를 돌려 실패를 확인한다**

Run: `./gradlew :RomRom-Web:test --tests "com.romrom.web.config.ApplicationYamlStructureTest"`

Expected: `존재하지_않는_flyway_속성이_없다` FAIL,
`flyway_검증_설정은_기존_값을_유지한다` PASS (변경 금지 가드)

- [ ] **Step 3: application.yml의 flyway 블록을 교체한다**

기존:

```yaml
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    sql-migration-suffixes: .sql
    validate-on-migrate: false # 마이그레이션 전 검증
    out-of-order: false
    repair-on-migrate: true  # 마이그레이션 전 자동 repair
```

교체 후:

```yaml
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    sql-migration-suffixes: .sql
    # 적용 완료된 마이그레이션 파일이 이후 수정된 이력이 있어 체크섬 검증을 끈 상태다.
    # 켜면 그 지점에서 배포가 즉시 중단되므로, 체크섬 정합화 작업 이후에 활성화한다.
    validate-on-migrate: false
    out-of-order: false
```

`repair-on-migrate` 줄을 삭제한다.

- [ ] **Step 4: 테스트를 돌려 통과를 확인한다**

Run: `./gradlew :RomRom-Web:test --tests "com.romrom.web.config.ApplicationYamlStructureTest"`

Expected: 10개 테스트 전부 PASS

- [ ] **Step 5: 커밋**

```bash
git add RomRom-Web/src/main/resources/application.yml \
        RomRom-Web/src/test/java/com/romrom/web/config/ApplicationYamlStructureTest.java
git commit -m "설정_인프라_위생_및_측정_기반_확보 : chore : 무시되던 Flyway repair-on-migrate 설정 제거(#{ISSUE})"
```

---

## Task 7: 런타임 바인딩 검증 테스트

YAML 구조 테스트는 "키가 올바른 위치에 있다"만 보장한다. Spring이 실제로 그 값을 바인딩하는지는 별도 검증이 필요하다. 특히 `open-in-view`는 D에서 `false`로 전환할 때 이 테스트가 전환 완료를 증명하는 장치가 된다.

**Files:**
- Test: `RomRom-Web/src/test/java/com/romrom/web/config/ConfigBindingTest.java` (생성)

**Interfaces:**
- Consumes: Task 2·3·5에서 확정된 설정값
- Produces: 없음

로컬 PostgreSQL·MongoDB·Redis가 필요하다 (기존 `OnlinePresenceServiceTest`와 동일한 전제). 인프라가 없으면 이 태스크는 건너뛰고 Task 8의 배포 후 실측으로 대체한다.

- [ ] **Step 1: 테스트 작성**

```java
package com.romrom.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.romrom.web.RomBackApplication;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * 설정값이 실제로 Spring 컨텍스트에 바인딩되는지 검증하는 통합 테스트.
 *
 * 로컬 PostgreSQL/MongoDB/Redis가 필요하다.
 * open-in-view 단언은 서브프로젝트 D에서 false로 전환할 때 전환 완료를 증명하는 장치다.
 */
@SpringBootTest(
    classes = RomBackApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Slf4j
class ConfigBindingTest {

  @Autowired
  Environment environment;

  @Autowired
  DataSource dataSource;

  @Autowired
  TestRestTemplate testRestTemplate;

  @Test
  @DisplayName("open-in-view가 Spring에 실제로 바인딩된다 (과거 키 위치 오류로 무시된 이력)")
  void openInView가_설정값대로_바인딩된다() {
    assertEquals("true", environment.getProperty("spring.jpa.open-in-view"));
  }

  @Test
  @DisplayName("HikariCP 풀 설정이 실제 DataSource에 반영된다")
  void 커넥션_풀_설정이_DataSource에_반영된다() {
    HikariDataSource hikariDataSource = assertInstanceOf(HikariDataSource.class, dataSource);

    assertEquals("RomRomHikariPool", hikariDataSource.getPoolName());
    assertEquals(20, hikariDataSource.getMaximumPoolSize());
    assertEquals(5, hikariDataSource.getMinimumIdle());
    assertEquals(5000, hikariDataSource.getLeakDetectionThreshold());
  }

  @Test
  @DisplayName("배치 페치 사이즈가 Hibernate에 전달된다")
  void 배치_페치_사이즈가_Hibernate에_전달된다() {
    assertEquals("100",
        environment.getProperty("spring.jpa.properties.hibernate.default_batch_fetch_size"));
  }

  @Test
  @DisplayName("health는 Dockerfile HEALTHCHECK가 쓰므로 인증 없이 열려 있어야 한다")
  void actuator_health는_인증없이_접근된다() {
    ResponseEntity<String> healthResponse =
        testRestTemplate.getForEntity("/actuator/health", String.class);

    assertEquals(HttpStatus.OK, healthResponse.getStatusCode());
  }

  @Test
  @DisplayName("노출 확대가 인증을 우회하지 않았는지 검증한다")
  void actuator_prometheus는_인증없이_접근되지_않는다() {
    ResponseEntity<String> prometheusResponse =
        testRestTemplate.getForEntity("/actuator/prometheus", String.class);

    org.junit.jupiter.api.Assertions.assertTrue(
        prometheusResponse.getStatusCode() == HttpStatus.UNAUTHORIZED
            || prometheusResponse.getStatusCode() == HttpStatus.FORBIDDEN,
        "인증 없이 prometheus에 접근됐다. 실제 응답: " + prometheusResponse.getStatusCode());
  }
}
```

- [ ] **Step 2: 테스트를 돌려 통과를 확인한다**

Run: `./gradlew :RomRom-Web:test --tests "com.romrom.web.config.ConfigBindingTest"`

Expected: 5개 테스트 PASS

`actuator_prometheus는_인증없이_접근되지_않는다`가 실패하면 **멈춘다.** `SecurityUrls.AUTH_WHITELIST`의 `/actuator/health` 항목이 prefix 매칭으로 동작해 `/actuator/**` 전체를 열어버린 경우다. 이때는 whitelist 패턴을 정확 매칭으로 좁히는 작업을 Task 5로 되돌려 처리한다.

컨텍스트 기동에 실패하면 로컬 인프라(PostgreSQL 5432, MongoDB 27017, Redis 6379)를 먼저 확인한다. 인프라가 없어 실행 불가하면 **테스트를 삭제하지 말고** 실행 불가 사실을 기록한 뒤 Task 8로 넘어간다.

- [ ] **Step 3: 커밋**

```bash
git add RomRom-Web/src/test/java/com/romrom/web/config/ConfigBindingTest.java
git commit -m "설정_인프라_위생_및_측정_기반_확보 : test : 설정 런타임 바인딩 검증 테스트 추가(#{ISSUE})"
```

---

## Task 8: 운영·테스트 프로필 로그레벨 반영 (GitHub Secret 갱신)

`application-prod.yml`은 CI가 GitHub Secret `APPLICATION_PROD_YML`에서 생성한다(`ROMROM-BE-CICD-BLUEGREEN.yaml:34-39`). `application-test.yml`도 마찬가지로 `APPLICATION_TEST_YML`에서 생성된다(`PROJECT-SPRING-SYNOLOGY-PR-PREVIEW.yaml:444-446`). **로컬 파일을 고쳐도 배포에 반영되지 않는다.** Secret 두 개를 모두 갱신해야 한다.

**Files:**
- Modify: GitHub Secret `APPLICATION_PROD_YML`
- Modify: GitHub Secret `APPLICATION_TEST_YML`
- Modify: `RomRom-Web/src/main/resources/application-prod.yml` (로컬 사본 — gitignore 대상, 동기화 목적)
- Modify: `RomRom-Web/src/main/resources/application-test.yml` (로컬 사본 — gitignore 대상, 동기화 목적)

**Interfaces:**
- Consumes: 없음
- Produces: 운영 로그레벨 INFO — Task 9의 실측에서 로그량 감소를 확인한다

- [ ] **Step 1: 현재 Secret 값을 로컬 사본 기준으로 확인한다**

로컬 `application-prod.yml` / `application-test.yml`이 Secret과 동일하다고 가정하지 않는다. 값이 다르면 **Secret이 정답**이다. `/pro-github` 스킬로 Secret 목록을 조회해 `APPLICATION_PROD_YML`과 `APPLICATION_TEST_YML` 존재를 확인한다.

- [ ] **Step 2: 두 파일의 로그레벨 블록을 동일하게 수정한다**

`application-prod.yml`과 `application-test.yml` **양쪽 모두** `logging.level` 블록에서 아래를 변경한다. 두 파일의 로그레벨 정의는 현재 동일하다.

변경 전:

```yaml
  level:
    com:
      romrom: DEBUG
    me:
      suhsaechan:
        suh-logger: DEBUG
        suh-api-log: DEBUG
```

변경 후:

```yaml
  level:
    com:
      romrom: INFO
    me:
      suhsaechan:
        suh-logger: INFO
        suh-api-log: INFO
```

나머지(`org.springframework: WARN` 등)는 그대로 둔다. `org.hibernate.SQL`은 **추가하지 않는다** — 미설정이 곧 출력 없음이다.

- [ ] **Step 3: 로컬 사본과 Secret을 함께 갱신한다**

1. 로컬 `application-prod.yml` / `application-test.yml`에 Step 2의 변경을 적용한다 (gitignore 대상이라 커밋되지 않는다)
2. `/pro-github` 스킬로 Secret `APPLICATION_PROD_YML`을 갱신한다 — **전체 파일 내용을 통째로** 넣는다. 부분 갱신은 불가능하다
3. 같은 방식으로 Secret `APPLICATION_TEST_YML`을 갱신한다

- [ ] **Step 4: Secret 반영을 확인한다**

Secret 값은 조회할 수 없으므로(GitHub가 write-only로 취급), 갱신 성공 응답만 확인한다. 실제 반영 검증은 Task 9의 배포 후 실측에서 한다.

- [ ] **Step 5: 커밋할 파일 없음**

이 태스크는 gitignore 대상 파일과 GitHub Secret만 다루므로 커밋이 없다. 다음 태스크로 넘어간다.

---

## Task 9: 배포 및 실측 검증

이 프로젝트의 CI는 `./gradlew clean build -x test`로 테스트를 건너뛴다. 따라서 테스트는 배포 게이트가 아니며, **배포 후 실측이 유일한 최종 검증**이다.

**Files:** 없음 (운영 검증)

**Interfaces:**
- Consumes: Task 2~8의 모든 변경
- Produces: p95 베이스라인 기록 — 서브프로젝트 B/C/D의 비교 기준

- [ ] **Step 1: main 최신화 후 배포**

```bash
git fetch origin
git log origin/main --oneline -5
```

`origin/main`에 새 커밋이 있으면 `git merge origin/main`으로 병합한다. 릴리스 워크플로우가 버전 커밋을 main에만 남기므로, 생략하면 다음 배포에서 버전 파일 충돌이 난다.

이후 `/pro-changelog-deploy` 스킬로 배포한다.

- [ ] **Step 2: 기동 로그로 설정 반영을 확인한다**

`/pro-ssh` 스킬로 서버에 접속해 확인한다.

확인 항목:
1. 풀 이름이 `RomRomHikariPool`로 바뀌었는가 — 기본값은 `HikariPool-1`이므로 이 이름 자체가 설정 반영의 증거다
2. `show_sql` 유래의 포맷 없는 raw SQL 출력이 사라졌는가
3. 로그레벨 INFO 반영 — `DEBUG` 레벨 라인이 `com.romrom` 로거에서 사라졌는가
4. 기동 예외가 없는가 — 특히 `default_batch_fetch_size` 관련 Hibernate 경고

`RomRomHikariPool`이 보이지 않으면 Task 3의 `application.yml` 변경이 이미지에 포함되지 않은 것이다. 배포 파이프라인을 먼저 확인한다.

- [ ] **Step 3: Prometheus 지표를 수집한다**

관리자 인증 후 `/actuator/prometheus`를 호출해 아래 지표가 존재하는지 확인한다.

- `hikaricp_connections_active`, `hikaricp_connections_pending`, `hikaricp_connections_usage_seconds`
- `hibernate_query_executions_total` 또는 `hibernate_statements_*`
- `http_server_requests_seconds_bucket`
- `jvm_gc_pause_seconds`, `jvm_memory_used_bytes`

인증 없이 호출했을 때 401/403이 반환되는지도 함께 확인한다 — 노출 확대가 인증을 우회하지 않았음을 검증한다.

- [ ] **Step 4: 커넥션 누수 경고를 수집한다**

`Connection leak detection triggered` 경고를 검색해 발생 지점과 빈도를 기록한다.

이 데이터는 **서브프로젝트 D의 OSIV 전환 판단 근거**다. 경고가 다수 발생한다면 OSIV가 실제로 커넥션을 오래 잡고 있다는 직접 증거가 된다. 경고가 없다면 OSIV 전환의 우선순위를 낮출 수 있다. 어느 쪽이든 결과를 기록한다.

- [ ] **Step 5: p95 베이스라인을 기록한다**

`http_server_requests_seconds` 히스토그램에서 아래 엔드포인트의 p95를 추출해 이슈 댓글로 남긴다.

- `POST /api/item/list` (메인 피드, 최다 호출)
- `POST /api/admin/dashboard/stats`
- `POST /api/admin/members/list`
- 채팅방 목록 조회 엔드포인트

측정 시각과 수집 기간을 함께 기록한다. 기록이 없으면 B/C/D에서 개선을 주장할 근거가 사라진다.

- [ ] **Step 6: 보고서 작성 및 라벨 정리**

1. `/pro-report` 스킬로 구현 보고서를 생성해 이슈에 댓글로 남긴다
2. Step 2~5의 실측 결과를 후속 댓글로 남긴다 — 특히 누수 경고 유무와 p95 베이스라인
3. `/pro-github` 스킬로 라벨을 `작업완료`로 **전체 교체**한다 (`set-labels` 사용 — `add-labels`만 쓰면 `작업전`이 남아 상태가 둘이 된다)
4. **이슈는 닫지 않는다**

---

## 완료 기준

1. `ApplicationYamlStructureTest` 10개 테스트 전부 통과
2. `ConfigBindingTest` 5개 통과 (로컬 인프라 있는 경우)
3. 운영 로그에서 raw SQL 전량 출력이 사라짐
4. `/actuator/prometheus`에서 풀·쿼리·HTTP 지표 수집 확인
5. 주요 4개 엔드포인트 p95 베이스라인이 이슈에 기록됨
6. 배포 후 신규 예외 없음
7. `ddl-auto`, `generate-ddl`, `validate-on-migrate` 값이 작업 전과 동일함 (Task 2·6의 가드 테스트로 보장)
