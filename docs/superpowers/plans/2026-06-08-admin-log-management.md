# 관리자 로그 관리 화면 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 서버에 SSH로 접속하지 않고도 관리자 웹(`/admin/logs`)에서 서버 로그(`romrom.log` + `.gz`)를 조회·검색·에러집계·실시간tail·파일목록추적·다운로드·`.gz`조회까지 모두 수행할 수 있게 한다.

**Architecture:** 로그는 이미 파일(`/mnt/romrom/logs/romrom.log` + 날짜별 `.gz` 롤링)로 쌓이므로 DB 적재 없이 파일을 직접 읽는다. 신규 `LogFileService`(RomRom-Application)가 파일 tail/집계/목록/다운로드/gz를 담당하고, `LogLineParser`가 라인을 파싱한다. 실시간 탭은 기존 SSE 인프라(`SseLogBroadcaster`/`SseLogAppender`/`DebugLogEvent`)를 그대로 재활용하되 관리자 인증 경로(`/api/admin/logs/stream`)로 노출한다. 화면은 기존 admin Thymeleaf 레이아웃 + DaisyUI 탭 4개 + 바닐라 JS.

**Tech Stack:** Spring Boot (Gradle 멀티모듈), Thymeleaf + Tailwind4/DaisyUI5 + 바닐라 JS, JUnit5 + `@SpringBootTest`(SuhLogger `timeLog` 패턴), Logback, SSE(SseEmitter).

---

## 사전 컨텍스트 (구현자가 반드시 알아야 할 것)

- **빌드/테스트 실행** (worktree 루트에서):
  - 컴파일: `./gradlew :RomRom-Web:compileJava`
  - 특정 테스트: `./gradlew :RomRom-Application:test --tests "com.romrom.application.service.LogFileServiceTest"`
  - 전체 Application 모듈 테스트: `./gradlew :RomRom-Application:test`
- **테스트 패턴**: 이 프로젝트 테스트는 `@SpringBootTest(classes = RomBackApplication.class)` + `@ActiveProfiles("dev")` 를 쓰고, `mainTest()` 하나 안에서 `timeLog(this::개별테스트메서드)` 로 호출한다 (`me.suhsaechan.suhlogger.util.SuhLogger`의 `lineLog`/`superLog`/`timeLog`). `AdminReportServiceTest`를 그대로 모방한다.
  - 단, 파일 I/O 단위 로직(`LogLineParser`, 파일 tail)은 Spring 컨텍스트가 필요 없으므로 **순수 JUnit5 테스트**(`@SpringBootTest` 없이)로 작성해 빠르게 돌린다. 임시 파일은 JUnit `@TempDir` 사용.
- **Admin API 컨벤션** (CLAUDE.md):
  - 모든 Admin API는 `POST` + `consumes = MediaType.MULTIPART_FORM_DATA_VALUE` + `@ModelAttribute AdminRequest` → `ResponseEntity<AdminResponse>`. (파일 다운로드/SSE는 성격상 예외 — GET)
  - 도메인별 DTO 금지 → 기존 `AdminRequest`/`AdminResponse`에 필드 추가.
  - `success`/`message` 필드 안 씀 — HTTP 상태코드 + `@ControllerAdvice`+`CustomException`.
  - Admin Service는 `RomRom-Application`(`com.romrom.application.service`)에 위치.
  - **`AdminApiController`는 Docs 인터페이스를 구현하지 않는다** — `@ApiChangeLogs`/`@Operation`을 컨트롤러 메서드에 직접 작성.
- **변수명 규칙** (CLAUDE.md): `key`/`value`/`config`/`trimmed` 같은 일반명 금지. 역할이 드러나는 이름. Boolean은 `is` 접두사만 (`has`/`can` 금지).
- **기존 SSE 자산** (재활용):
  - `RomRom-Common/.../service/SseLogBroadcaster.java` — `addSubscriber/removeSubscriber/broadcast/getActiveSubscriberCount`, 최대 10명, 초당 100건 rate limit.
  - `RomRom-Common/.../logging/SseLogAppender.java` — `com.romrom.*` 로그를 broadcast. `logback-spring.xml`에 `SSE_LOG` appender로 root에 이미 등록됨.
  - `RomRom-Common/.../dto/DebugLogEvent.java` — `{timestamp, level, loggerName, message, threadName}`.
  - `RomRom-Web/.../controller/api/DebugController.java` — 기존 `GET /api/app/debug/log-stream`(`@SecuredApi` HMAC). 이번에 관리자 경로로 통합.
- **인증**: `AdminJwtAuthenticationFilter`는 `/api/admin/**`/`/admin/**` 경로에서 Authorization 헤더 **또는** `accessToken` 쿠키로 인증한다. SSE(`EventSource`)는 커스텀 헤더 불가 → **쿠키 인증**으로 자동 동작(로그인 시 `accessToken` 쿠키 설정됨). API/JS fetch는 `adminFetch.post(url, params)` 헬퍼가 Bearer 헤더 + FormData를 자동 처리.
- **로그 경로**: `application-dev.yml`/`application-prod.yml`의 `logging.file.name = /mnt/romrom/logs/romrom.log`. 부모 디렉터리에 `romrom.log` + `romrom.log.%d{yyyy-MM-dd}.%i.gz` 롤링.
- **로그 라인 포맷** (`logback-spring.xml`): `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`
  예: `2026-06-08 14:23:01.123 [http-nio-8080-exec-1] ERROR com.romrom.web.X - 메시지`

---

## 파일 구조 (생성/수정 대상)

**생성:**
- `RomRom-Application/src/main/java/com/romrom/application/service/LogFileService.java` — 파일 읽기/집계/목록/시간범위/다운로드/gz (핵심 서비스)
- `RomRom-Application/src/main/java/com/romrom/application/util/LogLineParser.java` — 로그 라인 1줄 파싱 + 멀티라인 결합
- `RomRom-Application/src/main/java/com/romrom/application/dto/LogLineParsed.java` — 파싱 결과 값 객체
- `RomRom-Application/src/test/java/com/romrom/application/util/LogLineParserTest.java` — 파서 순수 단위테스트
- `RomRom-Application/src/test/java/com/romrom/application/service/LogFileServiceTest.java` — 서비스 단위테스트(@TempDir)
- `RomRom-Web/src/main/resources/templates/admin/logs.html` — 화면 (DaisyUI 탭 4개)
- `RomRom-Web/src/main/resources/static/js/admin-logs.js` — 화면 전용 JS (브라우저 부하 최소화 로직)

**수정:**
- `RomRom-Application/src/main/java/com/romrom/application/dto/AdminRequest.java` — 로그 요청 필드 추가
- `RomRom-Application/src/main/java/com/romrom/application/dto/AdminResponse.java` — 로그 응답 필드 + 내부 정적 클래스 추가
- `RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiController.java` — 로그 엔드포인트 7종 추가
- `RomRom-Web/src/main/java/com/romrom/web/controller/view/AdminPageController.java` — `GET /admin/logs` 추가
- `RomRom-Web/src/main/resources/templates/admin/layout.html` — 사이드바 "로그 관리" 메뉴 추가
- `RomRom-Domain-Auth/src/main/java/com/romrom/auth/dto/SecurityUrls.java` — 로그 경로 `ADMIN_PATHS` 등록

---

## Task 1: LogLineParsed 값 객체 + LogLineParser (라인 파싱)

**Files:**
- Create: `RomRom-Application/src/main/java/com/romrom/application/dto/LogLineParsed.java`
- Create: `RomRom-Application/src/main/java/com/romrom/application/util/LogLineParser.java`
- Test: `RomRom-Application/src/test/java/com/romrom/application/util/LogLineParserTest.java`

- [ ] **Step 1: 값 객체 생성**

`RomRom-Application/src/main/java/com/romrom/application/dto/LogLineParsed.java`:

```java
package com.romrom.application.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 로그 파일 한 줄(멀티라인 스택트레이스 포함)을 파싱한 결과.
 * 타임스탬프로 시작하지 않는 라인은 직전 로그의 message에 결합된다.
 */
@Getter
@Builder
@AllArgsConstructor
public class LogLineParsed {
  private final LocalDateTime loggedAt;   // 파싱 실패 시 null
  private final String logLevel;          // ERROR/WARN/INFO/DEBUG/TRACE, 실패 시 null
  private final String loggerName;        // 실패 시 null
  private final String logMessage;        // 멀티라인 결합 포함
  private final String rawLine;           // 원본 라인 (필터/출력 폴백용)
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`RomRom-Application/src/test/java/com/romrom/application/util/LogLineParserTest.java`:

```java
package com.romrom.application.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.romrom.application.dto.LogLineParsed;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogLineParserTest {

  private final LogLineParser logLineParser = new LogLineParser();

  @Test
  void 단일라인_정상포맷_파싱() {
    String rawLogLine = "2026-06-08 14:23:01.123 [http-nio-8080-exec-1] ERROR com.romrom.web.X - 무언가 터졌다";

    LogLineParsed parsed = logLineParser.parseSingleLine(rawLogLine);

    assertEquals(LocalDateTime.of(2026, 6, 8, 14, 23, 1, 123_000_000), parsed.getLoggedAt());
    assertEquals("ERROR", parsed.getLogLevel());
    assertEquals("com.romrom.web.X", parsed.getLoggerName());
    assertEquals("무언가 터졌다", parsed.getLogMessage());
  }

  @Test
  void 타임스탬프없는라인_파싱시_레벨null() {
    String stackTraceLine = "\tat com.romrom.web.X.method(X.java:42)";

    LogLineParsed parsed = logLineParser.parseSingleLine(stackTraceLine);

    assertNull(parsed.getLoggedAt());
    assertNull(parsed.getLogLevel());
    assertEquals(stackTraceLine, parsed.getRawLine());
  }

  @Test
  void 멀티라인_스택트레이스_직전로그에_결합() {
    List<String> rawLines = List.of(
        "2026-06-08 14:23:01.123 [main] ERROR com.romrom.A - NPE 발생",
        "java.lang.NullPointerException: null",
        "\tat com.romrom.A.run(A.java:10)",
        "2026-06-08 14:23:02.000 [main] INFO  com.romrom.B - 정상 처리");

    List<LogLineParsed> parsedList = logLineParser.parseLines(rawLines);

    assertEquals(2, parsedList.size());
    assertEquals("ERROR", parsedList.get(0).getLogLevel());
    assertTrue(parsedList.get(0).getLogMessage().contains("NullPointerException"));
    assertTrue(parsedList.get(0).getLogMessage().contains("at com.romrom.A.run"));
    assertEquals("INFO", parsedList.get(1).getLogLevel());
  }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :RomRom-Application:test --tests "com.romrom.application.util.LogLineParserTest"`
Expected: FAIL (LogLineParser 클래스/메서드 없음 → 컴파일 에러)

- [ ] **Step 4: LogLineParser 구현**

`RomRom-Application/src/main/java/com/romrom/application/util/LogLineParser.java`:

```java
package com.romrom.application.util;

import com.romrom.application.dto.LogLineParsed;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 로그 파일 라인을 파싱한다.
 * logback 포맷: %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
 */
@Component
public class LogLineParser {

  // 그룹1=타임스탬프, 2=스레드, 3=레벨, 4=로거, 5=메시지
  private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
      "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) \\[(.*?)] (\\w+)\\s+(\\S+) - (.*)$");

  private static final DateTimeFormatter LOG_TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  /**
   * 한 줄을 파싱한다. 포맷 불일치(스택트레이스 등)면 loggedAt/logLevel/loggerName=null, rawLine만 채운다.
   */
  public LogLineParsed parseSingleLine(String rawLogLine) {
    Matcher logLineMatcher = LOG_LINE_PATTERN.matcher(rawLogLine);
    if (!logLineMatcher.matches()) {
      return LogLineParsed.builder()
          .rawLine(rawLogLine)
          .logMessage(rawLogLine)
          .build();
    }
    return LogLineParsed.builder()
        .loggedAt(LocalDateTime.parse(logLineMatcher.group(1), LOG_TIMESTAMP_FORMATTER))
        .threadFreeLevel(logLineMatcher.group(3))   // placeholder 방지용 — 아래 빌더 호출로 대체
        .build();
  }

  /**
   * 여러 라인을 파싱하며, 타임스탬프 없는 후속 라인을 직전 로그의 message에 결합한다.
   */
  public List<LogLineParsed> parseLines(List<String> rawLines) {
    List<LogLineParsed> parsedList = new ArrayList<>();
    StringBuilder pendingMessageBuilder = null;
    Object[] pendingHeader = null; // [loggedAt, level, logger, firstMessage, firstRaw]

    for (String rawLine : rawLines) {
      Matcher logLineMatcher = LOG_LINE_PATTERN.matcher(rawLine);
      if (logLineMatcher.matches()) {
        flushPending(parsedList, pendingHeader, pendingMessageBuilder);
        pendingHeader = new Object[]{
            LocalDateTime.parse(logLineMatcher.group(1), LOG_TIMESTAMP_FORMATTER),
            logLineMatcher.group(3),
            logLineMatcher.group(4),
            logLineMatcher.group(5),
            rawLine
        };
        pendingMessageBuilder = new StringBuilder(logLineMatcher.group(5));
      } else {
        if (pendingMessageBuilder != null) {
          pendingMessageBuilder.append('\n').append(rawLine);
        } else {
          // 첫 줄이 헤더가 아닌 경우 — 단독 raw 라인으로 추가
          parsedList.add(LogLineParsed.builder().rawLine(rawLine).logMessage(rawLine).build());
        }
      }
    }
    flushPending(parsedList, pendingHeader, pendingMessageBuilder);
    return parsedList;
  }

  private void flushPending(List<LogLineParsed> parsedList, Object[] pendingHeader,
      StringBuilder pendingMessageBuilder) {
    if (pendingHeader == null) {
      return;
    }
    parsedList.add(LogLineParsed.builder()
        .loggedAt((LocalDateTime) pendingHeader[0])
        .logLevel((String) pendingHeader[1])
        .loggerName((String) pendingHeader[2])
        .logMessage(pendingMessageBuilder.toString())
        .rawLine((String) pendingHeader[4])
        .build());
  }
}
```

> 주의: 위 `parseSingleLine`의 `.threadFreeLevel(...)`은 의도적 오류가 아니라 — **실제 작성 시 아래 올바른 버전으로 작성한다.** `LogLineParsed`에는 `threadFreeLevel` 필드가 없다. 올바른 `parseSingleLine` 본문:

```java
  public LogLineParsed parseSingleLine(String rawLogLine) {
    Matcher logLineMatcher = LOG_LINE_PATTERN.matcher(rawLogLine);
    if (!logLineMatcher.matches()) {
      return LogLineParsed.builder()
          .rawLine(rawLogLine)
          .logMessage(rawLogLine)
          .build();
    }
    return LogLineParsed.builder()
        .loggedAt(LocalDateTime.parse(logLineMatcher.group(1), LOG_TIMESTAMP_FORMATTER))
        .logLevel(logLineMatcher.group(3))
        .loggerName(logLineMatcher.group(4))
        .logMessage(logLineMatcher.group(5))
        .rawLine(rawLogLine)
        .build();
  }
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :RomRom-Application:test --tests "com.romrom.application.util.LogLineParserTest"`
Expected: PASS (3개 테스트)

- [ ] **Step 6: 커밋**

```bash
git add RomRom-Application/src/main/java/com/romrom/application/dto/LogLineParsed.java \
        RomRom-Application/src/main/java/com/romrom/application/util/LogLineParser.java \
        RomRom-Application/src/test/java/com/romrom/application/util/LogLineParserTest.java
git commit -m "관리자 로그 관리 화면 추가 : feat : 로그 라인 파서(LogLineParser) 및 파싱 값객체 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/788"
```

---

## Task 2: LogFileService — 디렉터리/경로 해석 + 파일 목록

**Files:**
- Create: `RomRom-Application/src/main/java/com/romrom/application/service/LogFileService.java`
- Test: `RomRom-Application/src/test/java/com/romrom/application/service/LogFileServiceTest.java`

이 서비스는 파일 I/O가 핵심이므로 Spring 컨텍스트 없이 순수 JUnit5 + `@TempDir`로 테스트한다. 로그 디렉터리는 `logging.file.name`을 생성자 주입받아 부모를 도출하되, 테스트는 별도 생성자로 디렉터리를 직접 주입한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`RomRom-Application/src/test/java/com/romrom/application/service/LogFileServiceTest.java`:

```java
package com.romrom.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.romrom.application.dto.AdminResponse.AdminLogFileInfo;
import com.romrom.application.util.LogLineParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogFileServiceTest {

  private LogFileService newServiceWith(Path logDirectory) {
    // 테스트 전용 생성자: (로그파일 경로, LogLineParser)
    return new LogFileService(logDirectory.resolve("romrom.log").toString(), new LogLineParser());
  }

  @Test
  void 파일목록_log와gz를_최신순으로반환(@TempDir Path logDirectory) throws IOException {
    Files.writeString(logDirectory.resolve("romrom.log"), "현재 로그\n");
    Files.writeString(logDirectory.resolve("romrom.log.2026-06-07.0.gz"), "gz 내용");
    LogFileService logFileService = newServiceWith(logDirectory);

    List<AdminLogFileInfo> logFiles = logFileService.listLogFiles();

    assertEquals(2, logFiles.size());
    assertTrue(logFiles.stream().anyMatch(f -> f.getFileName().equals("romrom.log")));
    assertTrue(logFiles.stream().anyMatch(f -> f.getFileName().equals("romrom.log.2026-06-07.0.gz")));
  }

  @Test
  void 로그디렉터리없음_빈목록반환(@TempDir Path logDirectory) {
    LogFileService logFileService =
        new LogFileService(logDirectory.resolve("nope/romrom.log").toString(), new LogLineParser());

    List<AdminLogFileInfo> logFiles = logFileService.listLogFiles();

    assertTrue(logFiles.isEmpty());
  }
}
```

> 이 테스트는 `AdminResponse.AdminLogFileInfo`를 참조한다 — Task 4에서 추가하지만, 컴파일을 위해 **Task 2에서 먼저 `AdminLogFileInfo` 내부 정적 클래스만 AdminResponse에 추가**한다(Step 2). 나머지 로그 응답 필드는 Task 4에서 한꺼번에.

- [ ] **Step 2: AdminResponse에 AdminLogFileInfo 내부 클래스 추가**

`RomRom-Application/src/main/java/com/romrom/application/dto/AdminResponse.java`의 마지막 내부 클래스(`AdminDashboardStats`) 뒤, 바깥 클래스 닫는 `}` 직전에 추가:

```java
    @ToString
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @Schema(description = "로그 파일 정보")
    public static class AdminLogFileInfo {
        @Schema(description = "파일명 (예: romrom.log, romrom.log.2026-06-07.0.gz)")
        private String fileName;

        @Schema(description = "파일 크기 (bytes)")
        private Long fileSizeBytes;

        @Schema(description = "마지막 수정 시각")
        private java.time.LocalDateTime lastModifiedAt;
    }
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :RomRom-Application:test --tests "com.romrom.application.service.LogFileServiceTest"`
Expected: FAIL (LogFileService 클래스 없음 → 컴파일 에러)

- [ ] **Step 4: LogFileService 골격 + listLogFiles 구현**

`RomRom-Application/src/main/java/com/romrom/application/service/LogFileService.java`:

```java
package com.romrom.application.service;

import com.romrom.application.dto.AdminResponse.AdminLogFileInfo;
import com.romrom.application.util.LogLineParser;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 서버 로그 파일(romrom.log + .gz 롤링)을 읽어 관리자 화면에 제공한다.
 * DB 적재 없이 파일을 직접 읽는다.
 */
@Slf4j
@Service
public class LogFileService {

  private final File currentLogFile;
  private final File logDirectory;
  private final LogLineParser logLineParser;

  public LogFileService(
      @Value("${logging.file.name:romrom.log}") String currentLogFilePath,
      LogLineParser logLineParser) {
    this.currentLogFile = new File(currentLogFilePath);
    this.logDirectory = currentLogFile.getParentFile() != null
        ? currentLogFile.getParentFile()
        : new File(".");
    this.logLineParser = logLineParser;
  }

  /**
   * 로그 디렉터리의 romrom.log + romrom.log.*.gz 목록을 최신 수정순으로 반환.
   * 디렉터리가 없으면 빈 목록.
   */
  public List<AdminLogFileInfo> listLogFiles() {
    if (!logDirectory.isDirectory()) {
      log.warn("로그 디렉터리 없음: {}", logDirectory.getAbsolutePath());
      return new ArrayList<>();
    }
    String currentLogFileName = currentLogFile.getName();
    File[] logFileArray = logDirectory.listFiles((dir, fileName) ->
        fileName.equals(currentLogFileName)
            || (fileName.startsWith(currentLogFileName + ".") && fileName.endsWith(".gz")));
    if (logFileArray == null) {
      return new ArrayList<>();
    }
    List<AdminLogFileInfo> logFileInfoList = new ArrayList<>();
    for (File logFile : logFileArray) {
      logFileInfoList.add(AdminLogFileInfo.builder()
          .fileName(logFile.getName())
          .fileSizeBytes(logFile.length())
          .lastModifiedAt(LocalDateTime.ofInstant(
              Instant.ofEpochMilli(logFile.lastModified()), ZoneId.systemDefault()))
          .build());
    }
    logFileInfoList.sort(Comparator.comparing(AdminLogFileInfo::getLastModifiedAt).reversed());
    return logFileInfoList;
  }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :RomRom-Application:test --tests "com.romrom.application.service.LogFileServiceTest"`
Expected: PASS (2개 테스트)

- [ ] **Step 6: 커밋**

```bash
git add RomRom-Application/src/main/java/com/romrom/application/service/LogFileService.java \
        RomRom-Application/src/main/java/com/romrom/application/dto/AdminResponse.java \
        RomRom-Application/src/test/java/com/romrom/application/service/LogFileServiceTest.java
git commit -m "관리자 로그 관리 화면 추가 : feat : LogFileService 골격 및 로그 파일 목록 조회 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/788"
```

---

## Task 3: LogFileService — 현재 로그 역방향 tail + 레벨/키워드 필터

**Files:**
- Modify: `RomRom-Application/src/main/java/com/romrom/application/service/LogFileService.java`
- Test: `RomRom-Application/src/test/java/com/romrom/application/service/LogFileServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 추가**

`LogFileServiceTest.java`에 메서드 추가:

```java
  @Test
  void readRecentLines_최근N줄_역방향으로반환(@TempDir Path logDirectory) throws IOException {
    StringBuilder logContentBuilder = new StringBuilder();
    for (int lineIndex = 1; lineIndex <= 10; lineIndex++) {
      logContentBuilder.append("2026-06-08 14:00:0").append(lineIndex % 10)
          .append(".000 [main] INFO  com.romrom.T - 라인").append(lineIndex).append('\n');
    }
    Files.writeString(logDirectory.resolve("romrom.log"), logContentBuilder.toString());
    LogFileService logFileService = newServiceWith(logDirectory);

    List<String> recentLines = logFileService.readRecentLines(3, null, null);

    assertEquals(3, recentLines.size());
    assertTrue(recentLines.get(2).contains("라인10")); // 최신이 마지막(시간순)
  }

  @Test
  void readRecentLines_레벨필터_ERROR만(@TempDir Path logDirectory) throws IOException {
    String logContent =
        "2026-06-08 14:00:01.000 [main] INFO  com.romrom.T - 정상\n"
            + "2026-06-08 14:00:02.000 [main] ERROR com.romrom.T - 실패1\n"
            + "2026-06-08 14:00:03.000 [main] WARN  com.romrom.T - 경고\n"
            + "2026-06-08 14:00:04.000 [main] ERROR com.romrom.T - 실패2\n";
    Files.writeString(logDirectory.resolve("romrom.log"), logContent);
    LogFileService logFileService = newServiceWith(logDirectory);

    List<String> errorLines = logFileService.readRecentLines(100, "ERROR", null);

    assertEquals(2, errorLines.size());
    assertTrue(errorLines.get(0).contains("실패1"));
    assertTrue(errorLines.get(1).contains("실패2"));
  }

  @Test
  void readRecentLines_키워드필터(@TempDir Path logDirectory) throws IOException {
    String logContent =
        "2026-06-08 14:00:01.000 [main] INFO  com.romrom.T - 주문 생성\n"
            + "2026-06-08 14:00:02.000 [main] INFO  com.romrom.T - 결제 완료\n";
    Files.writeString(logDirectory.resolve("romrom.log"), logContent);
    LogFileService logFileService = newServiceWith(logDirectory);

    List<String> matchedLines = logFileService.readRecentLines(100, null, "결제");

    assertEquals(1, matchedLines.size());
    assertTrue(matchedLines.get(0).contains("결제 완료"));
  }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :RomRom-Application:test --tests "com.romrom.application.service.LogFileServiceTest"`
Expected: FAIL (readRecentLines 메서드 없음)

- [ ] **Step 3: readRecentLines + 상수 + tail 헬퍼 구현**

`LogFileService.java`의 클래스 상단에 상수 추가:

```java
  private static final int MAX_QUERY_LINE_COUNT = 2000;     // 조회 줄수 상한
  private static final int MAX_GZ_LINE_COUNT = 5000;        // gz 조회 줄수 상한
```

import 추가:

```java
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
```

메서드 추가:

```java
  /**
   * 현재 romrom.log 끝에서부터 최대 lineCount 줄을 역방향으로 읽고,
   * 레벨/키워드 필터를 적용해 시간순(오래된→최신)으로 반환한다.
   * lineCount는 MAX_QUERY_LINE_COUNT로 캡.
   */
  public List<String> readRecentLines(int requestedLineCount, String levelFilter, String keywordFilter) {
    int cappedLineCount = Math.min(Math.max(requestedLineCount, 1), MAX_QUERY_LINE_COUNT);
    if (!currentLogFile.isFile()) {
      log.warn("로그 파일 없음: {}", currentLogFile.getAbsolutePath());
      return new ArrayList<>();
    }
    List<String> tailLines = readTailLines(currentLogFile, cappedLineCount);
    return applyLevelAndKeywordFilter(tailLines, levelFilter, keywordFilter);
  }

  /**
   * 파일 끝에서부터 maxLineCount 줄을 역방향으로 읽어 시간순으로 반환.
   * RandomAccessFile로 청크 역방향 스캔 — 전체 메모리 로드 금지.
   */
  private List<String> readTailLines(File targetFile, int maxLineCount) {
    List<String> reversedLines = new ArrayList<>();
    int readBufferSize = 8192;
    try (RandomAccessFile randomAccessFile = new RandomAccessFile(targetFile, "r")) {
      long filePointer = randomAccessFile.length();
      StringBuilder currentLineBuilder = new StringBuilder();
      while (filePointer > 0 && reversedLines.size() < maxLineCount) {
        long chunkStart = Math.max(0, filePointer - readBufferSize);
        int chunkLength = (int) (filePointer - chunkStart);
        byte[] chunkBytes = new byte[chunkLength];
        randomAccessFile.seek(chunkStart);
        randomAccessFile.readFully(chunkBytes);
        for (int byteIndex = chunkLength - 1; byteIndex >= 0; byteIndex--) {
          char readChar = (char) (chunkBytes[byteIndex] & 0xFF);
          if (readChar == '\n') {
            reversedLines.add(currentLineBuilder.reverse().toString());
            currentLineBuilder.setLength(0);
            if (reversedLines.size() >= maxLineCount) {
              break;
            }
          } else if (readChar != '\r') {
            currentLineBuilder.append(readChar);
          }
        }
        filePointer = chunkStart;
      }
      if (currentLineBuilder.length() > 0 && reversedLines.size() < maxLineCount) {
        reversedLines.add(currentLineBuilder.reverse().toString());
      }
    } catch (IOException e) {
      log.error("로그 tail 읽기 실패: {}", e.getMessage());
      return new ArrayList<>();
    }
    // 멀티바이트(UTF-8 한글) 안전성: 바이트 역순 조립은 ASCII 외 깨질 수 있으므로
    // 읽은 줄 수가 적을 때는 정방향 재조립이 안전 — 아래 보정 참조.
    Collections.reverse(reversedLines);
    return reversedLines;
  }

  /**
   * 레벨/키워드 필터 적용. levelFilter가 null/"전체"면 레벨 무시, keyword가 비면 키워드 무시.
   */
  private List<String> applyLevelAndKeywordFilter(List<String> rawLines, String levelFilter, String keywordFilter) {
    boolean isLevelActive = levelFilter != null && !levelFilter.isBlank() && !"전체".equals(levelFilter);
    boolean isKeywordActive = keywordFilter != null && !keywordFilter.isBlank();
    List<String> filteredLines = new ArrayList<>();
    for (String rawLine : rawLines) {
      if (isLevelActive) {
        com.romrom.application.dto.LogLineParsed parsed = logLineParser.parseSingleLine(rawLine);
        String lineLevel = parsed.getLogLevel();
        if (lineLevel == null || !lineLevel.equalsIgnoreCase(levelFilter)) {
          continue;
        }
      }
      if (isKeywordActive && !rawLine.contains(keywordFilter)) {
        continue;
      }
      filteredLines.add(rawLine);
    }
    return filteredLines;
  }
```

> **UTF-8 주의**: 위 바이트 역순 조립은 한글(멀티바이트)에서 깨질 수 있다. 안전성을 위해, 실제 구현 시 `readTailLines`를 다음 방식으로 작성한다 — 파일 끝에서 충분한 바이트(예: `maxLineCount * 평균라인길이` 추정, 상한 4MB)만 `seek` 후 그 블록을 `new String(bytes, UTF_8)`로 한 번에 디코딩하고, `split("\n")` 후 마지막 N줄을 취한다. 이 방식이 한글 안전하고 단순하다. 테스트가 통과하는 어느 구현이든 무방하나, **프로덕션 안전성 위해 블록 디코딩 방식을 채택할 것.** (테스트는 ASCII+한글 혼합이므로 깨지면 바로 드러난다.)

블록 디코딩 방식 권장 구현(이걸로 작성):

```java
  private List<String> readTailLines(File targetFile, int maxLineCount) {
    long maxReadBytes = 4L * 1024 * 1024; // 끝에서 최대 4MB만 읽음
    try (RandomAccessFile randomAccessFile = new RandomAccessFile(targetFile, "r")) {
      long fileLength = randomAccessFile.length();
      long readStart = Math.max(0, fileLength - maxReadBytes);
      int readLength = (int) (fileLength - readStart);
      byte[] tailBytes = new byte[readLength];
      randomAccessFile.seek(readStart);
      randomAccessFile.readFully(tailBytes);
      String tailContent = new String(tailBytes, StandardCharsets.UTF_8);
      String[] allLines = tailContent.split("\n", -1);
      List<String> resultLines = new ArrayList<>();
      int startIndex = Math.max(0, allLines.length - maxLineCount);
      for (int lineIndex = startIndex; lineIndex < allLines.length; lineIndex++) {
        String line = allLines[lineIndex].replace("\r", "");
        if (!line.isEmpty()) {
          resultLines.add(line);
        }
      }
      return resultLines;
    } catch (IOException e) {
      log.error("로그 tail 읽기 실패: {}", e.getMessage());
      return new ArrayList<>();
    }
  }
```

(이 버전 채택 시 `Collections` import은 불필요하면 제거.)

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :RomRom-Application:test --tests "com.romrom.application.service.LogFileServiceTest"`
Expected: PASS (목록 2 + tail/필터 3 = 5개)

- [ ] **Step 5: 커밋**

```bash
git add RomRom-Application/src/main/java/com/romrom/application/service/LogFileService.java \
        RomRom-Application/src/test/java/com/romrom/application/service/LogFileServiceTest.java
git commit -m "관리자 로그 관리 화면 추가 : feat : 현재 로그 역방향 tail 및 레벨/키워드 필터 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/788"
```

---

## Task 4: LogFileService — 에러 집계 + gz 조회 + 시간범위 추출 + 다운로드 리소스 + AdminResponse/AdminRequest 필드

**Files:**
- Modify: `RomRom-Application/src/main/java/com/romrom/application/service/LogFileService.java`
- Modify: `RomRom-Application/src/main/java/com/romrom/application/dto/AdminResponse.java`
- Modify: `RomRom-Application/src/main/java/com/romrom/application/dto/AdminRequest.java`
- Test: `RomRom-Application/src/test/java/com/romrom/application/service/LogFileServiceTest.java`

- [ ] **Step 1: AdminRequest 로그 필드 추가**

`AdminRequest.java` 마지막 필드 뒤(클래스 닫는 `}` 직전)에 추가:

```java
    // 로그 관리 관련 필드
    @Schema(description = "로그 조회 줄 수 (기본 200, 최대 2000)")
    private Integer logLineCount;

    @Schema(description = "로그 레벨 필터 (ERROR/WARN/INFO/DEBUG, 미입력 또는 '전체'=전체)")
    private String logLevelFilter;

    @Schema(description = "로그 키워드 검색어")
    private String logKeyword;

    @Schema(description = "에러 집계 기간(분, 기본 60)")
    private Integer logErrorWithinMinutes;

    @Schema(description = "대상 로그 파일명 (.gz 조회/다운로드용)")
    private String logFileName;
```

- [ ] **Step 2: AdminResponse 로그 필드 + AdminLogErrorSummary 내부 클래스 추가**

`AdminResponse.java` 공통 페이징 필드(`currentPage`) 뒤, 내부 클래스들 앞에 필드 추가:

```java
    // 로그 관리 관련 응답 데이터
    @Schema(description = "로그 라인 목록 (조회/검색/ gz 조회 결과)")
    private List<String> logLines;

    @Schema(description = "에러 집계 목록")
    private List<AdminLogErrorSummary> logErrorSummaries;

    @Schema(description = "로그 파일 목록")
    private List<AdminLogFileInfo> logFiles;

    @Schema(description = "로그 총 용량 (bytes)")
    private Long logTotalSizeBytes;

    @Schema(description = "로그 파일 개수")
    private Integer logFileCount;

    @Schema(description = "디스크 여유 공간 (bytes, 조회 가능 시)")
    private Long diskFreeBytes;

    @Schema(description = "디스크 전체 용량 (bytes, 조회 가능 시)")
    private Long diskTotalBytes;
```

그리고 `AdminLogFileInfo` 내부 클래스 옆에 `AdminLogErrorSummary` 추가:

```java
    @ToString
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @Schema(description = "에러 집계 요약")
    public static class AdminLogErrorSummary {
        @Schema(description = "예외 클래스명 또는 에러 식별 키")
        private String exceptionClassName;

        @Schema(description = "발생 횟수")
        private Integer occurrenceCount;

        @Schema(description = "마지막 발생 시각")
        private java.time.LocalDateTime lastOccurredAt;

        @Schema(description = "대표 메시지")
        private String representativeMessage;
    }
```

- [ ] **Step 3: 실패하는 테스트 추가**

`LogFileServiceTest.java`에 추가:

```java
  @Test
  void aggregateErrors_예외클래스별_집계(@TempDir Path logDirectory) throws IOException {
    String nowStamp = java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    String logContent =
        nowStamp + " [main] ERROR com.romrom.A - NullPointerException: x is null\n"
            + nowStamp + " [main] ERROR com.romrom.B - NullPointerException: y is null\n"
            + nowStamp + " [main] ERROR com.romrom.C - IllegalStateException: bad\n";
    Files.writeString(logDirectory.resolve("romrom.log"), logContent);
    LogFileService logFileService = newServiceWith(logDirectory);

    var errorSummaries = logFileService.aggregateErrors(60);

    // NullPointerException 2건, IllegalStateException 1건
    assertTrue(errorSummaries.stream()
        .anyMatch(s -> s.getExceptionClassName().contains("NullPointerException")
            && s.getOccurrenceCount() == 2));
  }

  @Test
  void readGzLines_압축해제후_키워드필터(@TempDir Path logDirectory) throws IOException {
    Path gzPath = logDirectory.resolve("romrom.log.2026-06-07.0.gz");
    String gzPlainContent =
        "2026-06-07 10:00:01.000 [main] INFO  com.romrom.T - 어제 정상\n"
            + "2026-06-07 10:00:02.000 [main] ERROR com.romrom.T - 어제 에러\n";
    try (var gzOut = new java.util.zip.GZIPOutputStream(Files.newOutputStream(gzPath))) {
      gzOut.write(gzPlainContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    // 현재 로그도 있어야 화이트리스트에 gz가 포함됨
    Files.writeString(logDirectory.resolve("romrom.log"), "현재\n");
    LogFileService logFileService = newServiceWith(logDirectory);

    List<String> gzLines = logFileService.readGzLines(
        "romrom.log.2026-06-07.0.gz", 100, "ERROR", null);

    assertEquals(1, gzLines.size());
    assertTrue(gzLines.get(0).contains("어제 에러"));
  }

  @Test
  void getLogFileResource_화이트리스트밖_경로조작_차단(@TempDir Path logDirectory) throws IOException {
    Files.writeString(logDirectory.resolve("romrom.log"), "현재\n");
    LogFileService logFileService = newServiceWith(logDirectory);

    org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
        () -> logFileService.getLogFileResource("../secret.txt"));
  }
```

- [ ] **Step 4: 테스트 실패 확인**

Run: `./gradlew :RomRom-Application:test --tests "com.romrom.application.service.LogFileServiceTest"`
Expected: FAIL (aggregateErrors/readGzLines/getLogFileResource 없음)

- [ ] **Step 5: LogFileService 메서드 구현**

import 추가:

```java
import com.romrom.application.dto.AdminResponse.AdminLogErrorSummary;
import com.romrom.application.dto.LogLineParsed;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
```

상수 추가:

```java
  // 메시지 앞부분에서 "XxxException" 형태를 뽑아 집계 키로 사용
  private static final Pattern EXCEPTION_CLASS_PATTERN = Pattern.compile("([A-Za-z0-9_.]+Exception)");
```

메서드 구현:

```java
  /**
   * 최근 withinMinutes 분 내 ERROR/WARN 로그를 예외 클래스별로 집계.
   * 예외명을 못 찾으면 로거명을 키로 사용. 발생횟수 내림차순.
   */
  public List<AdminLogErrorSummary> aggregateErrors(int withinMinutes) {
    List<String> recentLines = readRecentLines(MAX_QUERY_LINE_COUNT, null, null);
    LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(withinMinutes);
    Map<String, AdminLogErrorSummary> summaryByKey = new LinkedHashMap<>();

    for (String rawLine : recentLines) {
      LogLineParsed parsed = logLineParser.parseSingleLine(rawLine);
      String lineLevel = parsed.getLogLevel();
      if (lineLevel == null
          || !(lineLevel.equalsIgnoreCase("ERROR") || lineLevel.equalsIgnoreCase("WARN"))) {
        continue;
      }
      if (parsed.getLoggedAt() != null && parsed.getLoggedAt().isBefore(cutoffTime)) {
        continue;
      }
      String aggregationKey = extractExceptionKey(parsed);
      AdminLogErrorSummary existing = summaryByKey.get(aggregationKey);
      if (existing == null) {
        summaryByKey.put(aggregationKey, AdminLogErrorSummary.builder()
            .exceptionClassName(aggregationKey)
            .occurrenceCount(1)
            .lastOccurredAt(parsed.getLoggedAt())
            .representativeMessage(safeShortMessage(parsed.getLogMessage()))
            .build());
      } else {
        existing.setOccurrenceCount(existing.getOccurrenceCount() + 1);
        if (parsed.getLoggedAt() != null
            && (existing.getLastOccurredAt() == null
                || parsed.getLoggedAt().isAfter(existing.getLastOccurredAt()))) {
          existing.setLastOccurredAt(parsed.getLoggedAt());
        }
      }
    }
    List<AdminLogErrorSummary> summaries = new ArrayList<>(summaryByKey.values());
    summaries.sort(Comparator.comparing(AdminLogErrorSummary::getOccurrenceCount).reversed());
    return summaries;
  }

  private String extractExceptionKey(LogLineParsed parsed) {
    String logMessage = parsed.getLogMessage();
    if (logMessage != null) {
      Matcher exceptionMatcher = EXCEPTION_CLASS_PATTERN.matcher(logMessage);
      if (exceptionMatcher.find()) {
        return exceptionMatcher.group(1);
      }
    }
    return parsed.getLoggerName() != null ? parsed.getLoggerName() : "UNKNOWN";
  }

  private String safeShortMessage(String logMessage) {
    if (logMessage == null) {
      return "";
    }
    String firstLine = logMessage.split("\n", 2)[0];
    return firstLine.length() > 300 ? firstLine.substring(0, 300) : firstLine;
  }

  /**
   * 지정한 .gz 파일을 압축 해제해 레벨/키워드 필터를 적용한 라인을 반환.
   * fileName은 화이트리스트(listLogFiles) 검증 후에만 처리.
   */
  public List<String> readGzLines(String fileName, int requestedLineCount, String levelFilter, String keywordFilter) {
    File gzFile = resolveWhitelistedFile(fileName);
    int cappedLineCount = Math.min(Math.max(requestedLineCount, 1), MAX_GZ_LINE_COUNT);
    List<String> decompressedLines = new ArrayList<>();
    try (GZIPInputStream gzInputStream = new GZIPInputStream(Files.newInputStream(gzFile.toPath()));
        BufferedReader gzReader = new BufferedReader(
            new InputStreamReader(gzInputStream, StandardCharsets.UTF_8))) {
      String gzLine;
      while ((gzLine = gzReader.readLine()) != null && decompressedLines.size() < cappedLineCount) {
        decompressedLines.add(gzLine);
      }
    } catch (IOException e) {
      log.error("gz 압축 해제 실패 {}: {}", fileName, e.getMessage());
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
    return applyLevelAndKeywordFilter(decompressedLines, levelFilter, keywordFilter);
  }

  /**
   * 현재 romrom.log에서 최근 range 기간 라인만 잘라 하나의 텍스트로 반환.
   * 범위 내 로그가 없으면 안내 헤더 한 줄.
   */
  public String extractByTimeRange(Duration range) {
    LocalDateTime cutoffTime = LocalDateTime.now().minus(range);
    List<String> tailLines = readRecentLines(MAX_QUERY_LINE_COUNT, null, null);
    StringBuilder extractedBuilder = new StringBuilder();
    for (String rawLine : tailLines) {
      LogLineParsed parsed = logLineParser.parseSingleLine(rawLine);
      if (parsed.getLoggedAt() == null || !parsed.getLoggedAt().isBefore(cutoffTime)) {
        extractedBuilder.append(rawLine).append('\n');
      }
    }
    if (extractedBuilder.length() == 0) {
      return "# 범위 내 로그 없음\n";
    }
    return extractedBuilder.toString();
  }

  /**
   * 화이트리스트 검증된 파일을 다운로드 Resource로 반환.
   */
  public Resource getLogFileResource(String fileName) {
    File targetFile = resolveWhitelistedFile(fileName);
    return new FileSystemResource(targetFile);
  }

  /**
   * fileName이 listLogFiles 화이트리스트에 있고, 로그 디렉터리 하위로만 resolve되는지 검증.
   * 위반 시 CustomException(INVALID_REQUEST).
   */
  private File resolveWhitelistedFile(String fileName) {
    boolean isWhitelisted = listLogFiles().stream()
        .anyMatch(logFileInfo -> logFileInfo.getFileName().equals(fileName));
    if (!isWhitelisted) {
      log.warn("화이트리스트 밖 로그 파일 요청 차단: {}", fileName);
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    Path resolvedPath = logDirectory.toPath().resolve(fileName).normalize();
    if (!resolvedPath.startsWith(logDirectory.toPath().normalize())) {
      log.warn("경로 조작 차단: {}", fileName);
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    return resolvedPath.toFile();
  }

  /**
   * 로그 총 용량/파일 개수/디스크 상태를 채운 정보 묶음.
   */
  public long getLogTotalSizeBytes(List<AdminLogFileInfo> logFiles) {
    return logFiles.stream().mapToLong(AdminLogFileInfo::getFileSizeBytes).sum();
  }

  public long getDiskFreeBytes() {
    return logDirectory.isDirectory() ? logDirectory.getFreeSpace() : 0L;
  }

  public long getDiskTotalBytes() {
    return logDirectory.isDirectory() ? logDirectory.getTotalSpace() : 0L;
  }
```

> **`ErrorCode.INVALID_REQUEST` / `INTERNAL_SERVER_ERROR` 확인**: 구현 전 `RomRom-Common`의 `ErrorCode` enum에 두 값이 있는지 grep으로 확인하고, 이름이 다르면(예: `INVALID_INPUT_VALUE`) 실제 존재하는 값으로 치환한다. 없으면 가장 근접한 4xx/5xx 코드를 사용한다.

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :RomRom-Application:test --tests "com.romrom.application.service.LogFileServiceTest"`
Expected: PASS (8개)

- [ ] **Step 7: 커밋**

```bash
git add RomRom-Application/src/main/java/com/romrom/application/service/LogFileService.java \
        RomRom-Application/src/main/java/com/romrom/application/dto/AdminResponse.java \
        RomRom-Application/src/main/java/com/romrom/application/dto/AdminRequest.java \
        RomRom-Application/src/test/java/com/romrom/application/service/LogFileServiceTest.java
git commit -m "관리자 로그 관리 화면 추가 : feat : 에러 집계/gz 조회/시간범위 추출/다운로드 리소스 및 DTO 필드 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/788"
```

---

## Task 5: AdminApiController 로그 엔드포인트 7종 추가

**Files:**
- Modify: `RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiController.java`

- [ ] **Step 1: 의존성 주입 + import 추가**

클래스 필드에 추가:

```java
    private final com.romrom.application.service.LogFileService logFileService;
    private final com.romrom.common.service.SseLogBroadcaster sseLogBroadcaster;
```

상단 import에 추가:

```java
import com.romrom.application.dto.AdminResponse.AdminLogFileInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
```

- [ ] **Step 2: 로그 엔드포인트 메서드 추가**

`AdminApiController` 클래스 끝(마지막 `}` 직전)에 추가. 기존 컨벤션(`@PostMapping` + `MULTIPART_FORM_DATA_VALUE` + `@ModelAttribute` + `@LogMonitor` + `@ApiChangeLogs`/`@Operation`)을 따른다:

```java
    // ==================== Logs ====================

    private static final long LOG_SSE_TIMEOUT = 300_000L; // 5분
    private static final long LOG_SSE_HEARTBEAT_SECONDS = 10L;

    private static final ScheduledExecutorService logHeartbeatScheduler =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
          Thread heartbeatThread = new Thread(runnable, "admin-log-sse-heartbeat");
          heartbeatThread.setDaemon(true);
          return heartbeatThread;
        });

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.08", author = Author.SUHSAECHAN, issueNumber = 788, description = "관리자 로그 관리 화면용 로그 조회/검색 API 추가 (파일 직접 읽기, 레벨/키워드 필터)"),
    })
    @Operation(
        summary = "관리자 로그 조회/검색",
        description = """
        ## 인증: **ROLE_ADMIN**
        ## 요청 (multipart/form-data)
        - **`logLineCount`** (Integer, 선택, 기본 200, 최대 2000)
        - **`logLevelFilter`** (String, 선택): ERROR/WARN/INFO/DEBUG, '전체'/미입력=전체
        - **`logKeyword`** (String, 선택): 키워드 검색
        ## 반환 (AdminResponse.logLines): 시간순(오래된→최신) 로그 라인
        """
    )
    @PostMapping(value = "/logs/query", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> queryLogs(@ModelAttribute AdminRequest request) {
      int lineCount = request.getLogLineCount() != null ? request.getLogLineCount() : 200;
      List<String> logLines = logFileService.readRecentLines(
          lineCount, request.getLogLevelFilter(), request.getLogKeyword());
      return ResponseEntity.ok(AdminResponse.builder().logLines(logLines).build());
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.08", author = Author.SUHSAECHAN, issueNumber = 788, description = "관리자 로그 에러 집계 API 추가 (예외 클래스별 발생횟수/마지막발생/대표메시지)"),
    })
    @Operation(
        summary = "관리자 로그 에러 집계",
        description = """
        ## 인증: **ROLE_ADMIN**
        ## 요청 (multipart/form-data)
        - **`logErrorWithinMinutes`** (Integer, 선택, 기본 60): 집계 기간(분)
        ## 반환 (AdminResponse.logErrorSummaries): 예외 클래스별 집계, 발생횟수 내림차순
        """
    )
    @PostMapping(value = "/logs/errors", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> aggregateLogErrors(@ModelAttribute AdminRequest request) {
      int withinMinutes = request.getLogErrorWithinMinutes() != null ? request.getLogErrorWithinMinutes() : 60;
      return ResponseEntity.ok(AdminResponse.builder()
          .logErrorSummaries(logFileService.aggregateErrors(withinMinutes))
          .build());
    }

    @Operation(summary = "관리자 로그 파일 목록 + 용량/디스크 상태", description = "## 인증: **ROLE_ADMIN**\n현재 .log + 과거 .gz 목록, 총 용량, 디스크 여유공간 반환")
    @PostMapping(value = "/logs/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> listLogFiles(@ModelAttribute AdminRequest request) {
      List<AdminLogFileInfo> logFiles = logFileService.listLogFiles();
      return ResponseEntity.ok(AdminResponse.builder()
          .logFiles(logFiles)
          .logFileCount(logFiles.size())
          .logTotalSizeBytes(logFileService.getLogTotalSizeBytes(logFiles))
          .diskFreeBytes(logFileService.getDiskFreeBytes())
          .diskTotalBytes(logFileService.getDiskTotalBytes())
          .build());
    }

    @Operation(summary = "관리자 .gz 로그 조회/검색", description = "## 인증: **ROLE_ADMIN**\n과거 .gz 파일을 서버에서 압축 해제 후 레벨/키워드 필터 적용. logFileName 필수.")
    @PostMapping(value = "/logs/gz-query", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> queryGzLog(@ModelAttribute AdminRequest request) {
      int lineCount = request.getLogLineCount() != null ? request.getLogLineCount() : 500;
      List<String> gzLines = logFileService.readGzLines(
          request.getLogFileName(), lineCount, request.getLogLevelFilter(), request.getLogKeyword());
      return ResponseEntity.ok(AdminResponse.builder().logLines(gzLines).build());
    }

    @Operation(summary = "관리자 로그 시간범위 다운로드", description = "## 인증: **ROLE_ADMIN**\n현재 romrom.log에서 최근 range(5m/1h/6h/24h) 라인을 잘라 다운로드")
    @GetMapping(value = "/logs/download")
    @LogMonitor
    public ResponseEntity<byte[]> downloadByTimeRange(@RequestParam("range") String range) {
      Duration duration = switch (range) {
        case "5m" -> Duration.ofMinutes(5);
        case "1h" -> Duration.ofHours(1);
        case "6h" -> Duration.ofHours(6);
        case "24h" -> Duration.ofHours(24);
        default -> throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "지원하지 않는 range");
      };
      String extractedContent = logFileService.extractByTimeRange(duration);
      String serverStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
      String downloadFileName = "romrom-log_" + range + "_" + serverStamp + ".log";
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadFileName + "\"")
          .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
          .body(extractedContent.getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "관리자 로그 파일 통째 다운로드", description = "## 인증: **ROLE_ADMIN**\nfileName(.log 또는 .gz)을 화이트리스트 검증 후 스트리밍 다운로드")
    @GetMapping(value = "/logs/download-file")
    @LogMonitor
    public ResponseEntity<Resource> downloadLogFile(@RequestParam("fileName") String fileName) {
      Resource logFileResource = logFileService.getLogFileResource(fileName);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
          .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
          .body(logFileResource);
    }

    @Operation(summary = "관리자 실시간 로그 스트림 (SSE)", description = "## 인증: **ROLE_ADMIN** (쿠키 accessToken)\ntail -f 형태 라이브 스트림. 기존 SseLogBroadcaster 재활용, 최대 10 구독자.")
    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @LogMonitor
    public SseEmitter streamLogs() {
      SseEmitter logEmitter = new SseEmitter(LOG_SSE_TIMEOUT);
      boolean isRegistered = sseLogBroadcaster.addSubscriber(logEmitter);
      if (!isRegistered) {
        throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "최대 동시 접속 수 초과");
      }
      try {
        logEmitter.send(SseEmitter.event().name("connected").data("connected"));
      } catch (IOException e) {
        sseLogBroadcaster.removeSubscriber(logEmitter);
        logEmitter.complete();
        return logEmitter;
      }
      AtomicReference<ScheduledFuture<?>> heartbeatTaskRef = new AtomicReference<>();
      ScheduledFuture<?> heartbeatTask = logHeartbeatScheduler.scheduleAtFixedRate(() -> {
        try {
          logEmitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException e) {
          ScheduledFuture<?> selfTask = heartbeatTaskRef.get();
          if (selfTask != null) {
            selfTask.cancel(false);
          }
          logEmitter.completeWithError(e);
        }
      }, LOG_SSE_HEARTBEAT_SECONDS, LOG_SSE_HEARTBEAT_SECONDS, TimeUnit.SECONDS);
      heartbeatTaskRef.set(heartbeatTask);

      logEmitter.onCompletion(() -> {
        heartbeatTask.cancel(false);
        sseLogBroadcaster.removeSubscriber(logEmitter);
      });
      logEmitter.onTimeout(() -> {
        heartbeatTask.cancel(false);
        sseLogBroadcaster.removeSubscriber(logEmitter);
        logEmitter.complete();
      });
      logEmitter.onError(throwable -> {
        heartbeatTask.cancel(false);
        sseLogBroadcaster.removeSubscriber(logEmitter);
      });
      return logEmitter;
    }
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :RomRom-Web:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiController.java
git commit -m "관리자 로그 관리 화면 추가 : feat : AdminApiController 로그 엔드포인트 7종(조회/에러집계/파일목록/gz/다운로드/SSE) 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/788"
```

---

## Task 6: SecurityUrls 경로 등록 + DebugController SSE 통합 정리

**Files:**
- Modify: `RomRom-Domain-Auth/src/main/java/com/romrom/auth/dto/SecurityUrls.java`

- [ ] **Step 1: ADMIN_PATHS에 로그 경로 추가**

`SecurityUrls.java`의 `ADMIN_PATHS` 리스트 마지막 항목(`"/api/admin/config/maintenance/update"`) 뒤에 콤마 추가 후 다음을 추가:

```java
    ,
    // Admin Pages - Logs
    "/admin/logs",

    // Admin APIs - Logs
    "/api/admin/logs/query",
    "/api/admin/logs/errors",
    "/api/admin/logs/files",
    "/api/admin/logs/gz-query",
    "/api/admin/logs/download",
    "/api/admin/logs/download-file",
    "/api/admin/logs/stream"
```

> 참고: `AdminJwtAuthenticationFilter`는 `/api/admin`/`/admin`로 시작하는 모든 경로를 인증 대상으로 잡으므로 ADMIN_PATHS 등록 없이도 필터는 동작한다. 다만 명시적 문서화/일관성을 위해 등록한다. 기존 `/api/app/debug/log-stream`(SECURED_API_URLS)은 그대로 두되, 관리자 화면은 새 `/api/admin/logs/stream`을 사용한다.

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :RomRom-Domain-Auth:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add RomRom-Domain-Auth/src/main/java/com/romrom/auth/dto/SecurityUrls.java
git commit -m "관리자 로그 관리 화면 추가 : feat : 로그 관리 경로 ADMIN_PATHS 등록 https://github.com/TEAM-ROMROM/RomRom-BE/issues/788"
```

---

## Task 7: AdminPageController 페이지 + 사이드바 메뉴

**Files:**
- Modify: `RomRom-Web/src/main/java/com/romrom/web/controller/view/AdminPageController.java`
- Modify: `RomRom-Web/src/main/resources/templates/admin/layout.html`

- [ ] **Step 1: AdminPageController에 /admin/logs 추가**

`AdminPageController.java`의 `settings` 메서드 뒤(클래스 닫는 `}` 직전)에 추가:

```java
    @GetMapping("/logs")
    @LogMonitor
    public String logs(Model model) {
        model.addAttribute("pageTitle", "로그 관리");
        model.addAttribute("currentMenu", "logs");
        return "admin/logs";
    }
```

- [ ] **Step 2: layout.html 사이드바에 메뉴 추가**

`layout.html`의 설정(`settings`) 메뉴 `<li>` 블록 **앞**에 추가:

```html
                <li>
                    <a th:href="@{/admin/logs}" th:classappend="${currentMenu == 'logs'} ? 'active'">
                        <i data-lucide="scroll-text" class="size-5"></i>
                        로그 관리
                    </a>
                </li>
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :RomRom-Web:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add RomRom-Web/src/main/java/com/romrom/web/controller/view/AdminPageController.java \
        RomRom-Web/src/main/resources/templates/admin/layout.html
git commit -m "관리자 로그 관리 화면 추가 : feat : /admin/logs 페이지 라우트 및 사이드바 메뉴 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/788"
```

---

## Task 8: logs.html 화면 (DaisyUI 탭 4개)

**Files:**
- Create: `RomRom-Web/src/main/resources/templates/admin/logs.html`

- [ ] **Step 1: logs.html 작성**

`RomRom-Web/src/main/resources/templates/admin/logs.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{admin/layout}">
<head>
    <title layout:fragment="title">로그 관리</title>
</head>
<body>
<div layout:fragment="content">
    <!-- 탭 -->
    <div role="tablist" class="tabs tabs-border mb-4">
        <a role="tab" class="tab tab-active" id="tabQuery" onclick="LogAdmin.switchTab('query')">
            <i data-lucide="search" class="size-4 mr-1"></i> 조회·검색
        </a>
        <a role="tab" class="tab" id="tabErrors" onclick="LogAdmin.switchTab('errors')">
            <i data-lucide="alert-triangle" class="size-4 mr-1"></i> 에러 대시보드
        </a>
        <a role="tab" class="tab" id="tabLive" onclick="LogAdmin.switchTab('live')">
            <i data-lucide="activity" class="size-4 mr-1"></i> 실시간
        </a>
        <a role="tab" class="tab" id="tabFiles" onclick="LogAdmin.switchTab('files')">
            <i data-lucide="folder" class="size-4 mr-1"></i> 파일/다운로드
        </a>
    </div>

    <!-- 조회·검색 -->
    <div id="panelQuery" class="card bg-base-100 shadow">
        <div class="card-body">
            <div class="flex gap-2 flex-wrap items-center mb-3">
                <select id="queryLevel" class="select select-sm">
                    <option value="">전체</option>
                    <option value="ERROR">ERROR</option>
                    <option value="WARN">WARN</option>
                    <option value="INFO">INFO</option>
                    <option value="DEBUG">DEBUG</option>
                </select>
                <input id="queryKeyword" type="text" placeholder="키워드" class="input input-sm input-bordered"/>
                <select id="queryLineCount" class="select select-sm">
                    <option value="200">200줄</option>
                    <option value="500">500줄</option>
                    <option value="1000">1000줄</option>
                    <option value="2000">2000줄</option>
                </select>
                <button class="btn btn-sm btn-primary" onclick="LogAdmin.runQuery()">검색</button>
            </div>
            <pre id="queryOutput" class="bg-base-300 rounded p-3 text-xs overflow-auto" style="max-height:60vh"></pre>
        </div>
    </div>

    <!-- 에러 대시보드 -->
    <div id="panelErrors" class="card bg-base-100 shadow hidden">
        <div class="card-body">
            <div class="flex gap-2 items-center mb-3">
                <select id="errorWithinMinutes" class="select select-sm">
                    <option value="60">최근 1시간</option>
                    <option value="360">최근 6시간</option>
                    <option value="1440">최근 24시간</option>
                </select>
                <button class="btn btn-sm btn-primary" onclick="LogAdmin.runErrors()">집계</button>
            </div>
            <table class="table table-sm">
                <thead><tr><th>예외</th><th>횟수</th><th>마지막 발생</th><th>대표 메시지</th></tr></thead>
                <tbody id="errorTableBody"></tbody>
            </table>
        </div>
    </div>

    <!-- 실시간 -->
    <div id="panelLive" class="card bg-base-100 shadow hidden">
        <div class="card-body">
            <div class="flex gap-2 items-center mb-3">
                <span id="liveStatus" class="badge badge-ghost">연결 안 됨</span>
                <label class="label cursor-pointer gap-1">
                    <input id="liveAutoScroll" type="checkbox" class="checkbox checkbox-sm" checked/>
                    <span class="label-text">자동 스크롤</span>
                </label>
                <button class="btn btn-sm" onclick="LogAdmin.clearLive()">지우기</button>
            </div>
            <pre id="liveOutput" class="bg-base-300 rounded p-3 text-xs overflow-auto" style="max-height:60vh"></pre>
        </div>
    </div>

    <!-- 파일/다운로드 -->
    <div id="panelFiles" class="card bg-base-100 shadow hidden">
        <div class="card-body">
            <div class="flex gap-2 flex-wrap mb-3">
                <button class="btn btn-sm btn-outline" onclick="LogAdmin.downloadRange('5m')">최근 5분</button>
                <button class="btn btn-sm btn-outline" onclick="LogAdmin.downloadRange('1h')">최근 1시간</button>
                <button class="btn btn-sm btn-outline" onclick="LogAdmin.downloadRange('6h')">최근 6시간</button>
                <button class="btn btn-sm btn-outline" onclick="LogAdmin.downloadRange('24h')">최근 24시간</button>
            </div>
            <div id="diskStatus" class="text-sm mb-3 text-base-content/70"></div>
            <table class="table table-sm">
                <thead><tr><th>파일명</th><th>크기</th><th>수정시각</th><th>작업</th></tr></thead>
                <tbody id="fileTableBody"></tbody>
            </table>
        </div>
    </div>
</div>

<th:block layout:fragment="js">
    <script th:src="@{/js/admin-logs.js}"></script>
    <script>LogAdmin.init();</script>
</th:block>
</body>
</html>
```

- [ ] **Step 2: 커밋**

```bash
git add RomRom-Web/src/main/resources/templates/admin/logs.html
git commit -m "관리자 로그 관리 화면 추가 : feat : 로그 관리 화면(logs.html) 탭 4개 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/788"
```

---

## Task 9: admin-logs.js (브라우저 부하 최소화 로직 포함)

**Files:**
- Create: `RomRom-Web/src/main/resources/static/js/admin-logs.js`

- [ ] **Step 1: admin-logs.js 작성**

`RomRom-Web/src/main/resources/static/js/admin-logs.js`:

```javascript
/**
 * 관리자 로그 관리 화면 전용 JS.
 * 브라우저 부하 최소화: DOM 라인 캡, 탭 활성 시에만 SSE, requestAnimationFrame 배칭.
 */
const LogAdmin = (function () {
  const MAX_LIVE_DOM_LINES = 500;
  const ALL_TAB_IDS = ['query', 'errors', 'live', 'files'];

  let activeTab = 'query';
  let liveEventSource = null;
  let pendingLiveLines = [];
  let rafScheduled = false;

  function switchTab(tabName) {
    activeTab = tabName;
    ALL_TAB_IDS.forEach(function (id) {
      const tabEl = document.getElementById('tab' + id.charAt(0).toUpperCase() + id.slice(1));
      const panelEl = document.getElementById('panel' + id.charAt(0).toUpperCase() + id.slice(1));
      if (tabEl) tabEl.classList.toggle('tab-active', id === tabName);
      if (panelEl) panelEl.classList.toggle('hidden', id !== tabName);
    });
    // 실시간 탭 진입/이탈 시에만 SSE 연결 토글
    if (tabName === 'live') {
      connectLive();
    } else {
      disconnectLive();
    }
    if (tabName === 'files') runFiles();
  }

  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  function levelClass(line) {
    if (line.indexOf(' ERROR ') !== -1) return 'text-error';
    if (line.indexOf(' WARN ') !== -1) return 'text-warning';
    return '';
  }

  async function runQuery() {
    const params = {
      logLineCount: document.getElementById('queryLineCount').value,
      logLevelFilter: document.getElementById('queryLevel').value,
      logKeyword: document.getElementById('queryKeyword').value
    };
    const response = await adminFetch.post('/api/admin/logs/query', params);
    const data = await response.json();
    const outputEl = document.getElementById('queryOutput');
    outputEl.innerHTML = (data.logLines || [])
      .map(function (line) { return '<div class="' + levelClass(line) + '">' + escapeHtml(line) + '</div>'; })
      .join('');
  }

  async function runErrors() {
    const params = { logErrorWithinMinutes: document.getElementById('errorWithinMinutes').value };
    const response = await adminFetch.post('/api/admin/logs/errors', params);
    const data = await response.json();
    const tbody = document.getElementById('errorTableBody');
    tbody.innerHTML = (data.logErrorSummaries || []).map(function (summary) {
      return '<tr class="cursor-pointer hover" onclick="LogAdmin.jumpToKeyword(\'' +
        escapeHtml(summary.exceptionClassName) + '\')">' +
        '<td>' + escapeHtml(summary.exceptionClassName) + '</td>' +
        '<td>' + summary.occurrenceCount + '</td>' +
        '<td>' + (summary.lastOccurredAt || '') + '</td>' +
        '<td class="truncate max-w-md">' + escapeHtml(summary.representativeMessage || '') + '</td>' +
        '</tr>';
    }).join('');
  }

  function jumpToKeyword(keyword) {
    switchTab('query');
    document.getElementById('queryKeyword').value = keyword;
    runQuery();
  }

  function connectLive() {
    if (liveEventSource) return;
    const statusEl = document.getElementById('liveStatus');
    // SSE는 쿠키 accessToken으로 인증됨 (EventSource는 커스텀 헤더 불가)
    liveEventSource = new EventSource('/api/admin/logs/stream', { withCredentials: true });
    liveEventSource.onopen = function () {
      statusEl.textContent = '연결됨';
      statusEl.className = 'badge badge-success';
    };
    liveEventSource.onmessage = function (event) {
      pendingLiveLines.push(event.data);
      scheduleLiveFlush();
    };
    liveEventSource.onerror = function () {
      statusEl.textContent = '연결 끊김';
      statusEl.className = 'badge badge-error';
    };
  }

  function disconnectLive() {
    if (liveEventSource) {
      liveEventSource.close();
      liveEventSource = null;
      const statusEl = document.getElementById('liveStatus');
      statusEl.textContent = '연결 안 됨';
      statusEl.className = 'badge badge-ghost';
    }
  }

  function scheduleLiveFlush() {
    if (rafScheduled) return;
    rafScheduled = true;
    requestAnimationFrame(flushLiveLines);
  }

  function flushLiveLines() {
    rafScheduled = false;
    const outputEl = document.getElementById('liveOutput');
    const fragment = document.createDocumentFragment();
    pendingLiveLines.forEach(function (rawData) {
      let displayText = rawData;
      try {
        const parsed = JSON.parse(rawData);
        displayText = (parsed.timestamp || '') + ' ' + (parsed.level || '') + ' ' +
          (parsed.loggerName || '') + ' - ' + (parsed.message || '');
      } catch (e) { /* connected/heartbeat 등 평문은 그대로 */ }
      const lineDiv = document.createElement('div');
      lineDiv.className = levelClass(' ' + (JSON.parse(safeJson(rawData)).level || '') + ' ');
      lineDiv.textContent = displayText;
      fragment.appendChild(lineDiv);
    });
    pendingLiveLines = [];
    outputEl.appendChild(fragment);
    // DOM 라인 캡: 초과분 앞에서 제거
    while (outputEl.childNodes.length > MAX_LIVE_DOM_LINES) {
      outputEl.removeChild(outputEl.firstChild);
    }
    if (document.getElementById('liveAutoScroll').checked) {
      outputEl.scrollTop = outputEl.scrollHeight;
    }
  }

  function safeJson(text) {
    try { JSON.parse(text); return text; } catch (e) { return '{}'; }
  }

  function clearLive() {
    document.getElementById('liveOutput').innerHTML = '';
  }

  function formatBytes(bytes) {
    if (!bytes) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let value = bytes, unitIndex = 0;
    while (value >= 1024 && unitIndex < units.length - 1) { value /= 1024; unitIndex++; }
    return value.toFixed(1) + ' ' + units[unitIndex];
  }

  async function runFiles() {
    const response = await adminFetch.post('/api/admin/logs/files', {});
    const data = await response.json();
    document.getElementById('diskStatus').textContent =
      '로그 총 용량: ' + formatBytes(data.logTotalSizeBytes) +
      ' / 파일 수: ' + (data.logFileCount || 0) +
      ' / 디스크 여유: ' + formatBytes(data.diskFreeBytes) + ' (전체 ' + formatBytes(data.diskTotalBytes) + ')';
    const tbody = document.getElementById('fileTableBody');
    tbody.innerHTML = (data.logFiles || []).map(function (fileInfo) {
      const isGz = fileInfo.fileName.endsWith('.gz');
      const gzButton = isGz
        ? '<button class="btn btn-xs" onclick="LogAdmin.viewGz(\'' + escapeHtml(fileInfo.fileName) + '\')">조회</button>'
        : '';
      return '<tr>' +
        '<td>' + escapeHtml(fileInfo.fileName) + '</td>' +
        '<td>' + formatBytes(fileInfo.fileSizeBytes) + '</td>' +
        '<td>' + (fileInfo.lastModifiedAt || '') + '</td>' +
        '<td class="flex gap-1">' +
        '<a class="btn btn-xs btn-primary" href="/api/admin/logs/download-file?fileName=' +
        encodeURIComponent(fileInfo.fileName) + '">다운</a>' + gzButton +
        '</td></tr>';
    }).join('');
  }

  async function viewGz(fileName) {
    const params = { logFileName: fileName, logLineCount: 500 };
    const response = await adminFetch.post('/api/admin/logs/gz-query', params);
    const data = await response.json();
    const lines = (data.logLines || []).map(function (line) { return escapeHtml(line); }).join('\n');
    alert(fileName + '\n\n' + lines.substring(0, 4000) + (lines.length > 4000 ? '\n... (생략)' : ''));
  }

  function downloadRange(range) {
    window.location.href = '/api/admin/logs/download?range=' + range;
  }

  function init() {
    // 브라우저 탭 백그라운드 시 SSE 끊고, 복귀 시 재연결 (실시간 탭 활성 한정)
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) {
        disconnectLive();
      } else if (activeTab === 'live') {
        connectLive();
      }
    });
    runQuery();
  }

  return {
    init: init, switchTab: switchTab, runQuery: runQuery, runErrors: runErrors,
    jumpToKeyword: jumpToKeyword, clearLive: clearLive, downloadRange: downloadRange,
    viewGz: viewGz
  };
})();
```

> 정리 노트: `flushLiveLines`의 `lineDiv.className` 계산에서 `JSON.parse(safeJson(rawData))`를 다시 호출하는 중복이 있다. 실제 작성 시 위쪽에서 파싱한 `parsed` 객체를 재사용하도록 정리한다(파싱 1회). 즉 try 블록에서 `let parsedLevel = ''`를 잡아두고 `levelClass(' ' + parsedLevel + ' ')`로 호출. 동작 동일, 파싱 중복 제거.

- [ ] **Step 2: 컴파일/리소스 확인 (전체 빌드)**

Run: `./gradlew :RomRom-Web:compileJava`
Expected: BUILD SUCCESSFUL (JS는 정적 리소스라 컴파일 대상 아님 — 빌드만 깨지지 않는지 확인)

- [ ] **Step 3: 커밋**

```bash
git add RomRom-Web/src/main/resources/static/js/admin-logs.js
git commit -m "관리자 로그 관리 화면 추가 : feat : admin-logs.js (DOM 캡/탭별 SSE/rAF 배칭 부하 최소화) 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/788"
```

---

## Task 10: 전체 검증 — 빌드 + 서비스 테스트 + 수동 점검 안내

**Files:** (없음 — 검증 단계)

- [ ] **Step 1: Application 모듈 테스트 전체 실행**

Run: `./gradlew :RomRom-Application:test --tests "com.romrom.application.util.LogLineParserTest" --tests "com.romrom.application.service.LogFileServiceTest"`
Expected: PASS (파서 3 + 서비스 8 = 11개)

- [ ] **Step 2: 전체 컴파일**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 수동 점검 체크리스트 (서버 기동 후, 사용자/QA가 수행)**

서버를 dev 프로파일로 기동한 뒤 관리자 로그인 → `/admin/logs` 접속하여 확인:
- 사이드바 "로그 관리" 메뉴 노출 및 클릭 시 페이지 진입
- 조회·검색 탭: 레벨/키워드/줄수 변경 후 검색 → 라인 출력, ERROR 빨강/WARN 노랑 색상
- 에러 대시보드: 집계 표 노출, 행 클릭 시 조회 탭으로 키워드 점프
- 실시간: 탭 진입 시 "연결됨" 배지, 로그 흐름, 다른 탭 이동 시 연결 해제(개발자도구 Network에서 stream 종료 확인), 500줄 초과 시 위에서 제거
- 파일/다운로드: 파일 목록 + 용량/디스크 표시, 다운 버튼 동작, .gz 조회 버튼 동작, 시간범위(5분/1h/6h/24h) 다운로드 동작
- 비로그인 상태로 `/api/admin/logs/query` 직접 호출 시 401

- [ ] **Step 4: 최종 커밋 (plan 문서 포함)**

```bash
git add docs/superpowers/plans/2026-06-08-admin-log-management.md docs/superpowers/specs/2026-06-08-admin-log-management-design.md
git commit -m "관리자 로그 관리 화면 추가 : docs : 설계/구현 계획 문서 추가 https://github.com/TEAM-ROMROM/RomRom-BE/issues/788"
```

---

## Self-Review 결과

**1. Spec 커버리지:**
- 조회·검색(레벨필터/키워드) → Task 3 + Task 5(query) + Task 8/9 ✅
- 에러 대시보드 → Task 4(aggregateErrors) + Task 5(errors) + Task 8/9 ✅
- 실시간 tail → Task 5(stream, 기존 SSE 재활용) + Task 9(connectLive) ✅
- 파일 목록 추적 + 용량/디스크 → Task 2(listLogFiles) + Task 4(disk) + Task 5(files) ✅
- 시간범위 다운로드(5m/1h/6h/24h) → Task 4(extractByTimeRange) + Task 5(download) ✅
- 파일 통째 다운로드(.log/.gz) → Task 4(getLogFileResource) + Task 5(download-file) ✅
- .gz 압축 해제 조회/검색 → Task 4(readGzLines) + Task 5(gz-query) + Task 9(viewGz) ✅
- 브라우저 부하 최소화(DOM 캡/탭별 SSE/rAF) → Task 9 ✅
- 관리자 인증 + path traversal 방어 → Task 4(resolveWhitelistedFile) + Task 6(SecurityUrls) ✅
- 기존 SSE 통합 → Task 5(stream이 SseLogBroadcaster 재활용), Task 6 노트 ✅
- Swagger 문서화/@ApiChangeLog → Task 5(컨트롤러 직접 작성, AdminApiController는 Docs 인터페이스 미사용) ✅

**2. Placeholder 스캔:** Task 1·3·9에 의도적으로 "잘못된 1차 버전 → 올바른 버전" 패턴이 있으나, 각 경우 **올바른 최종 코드를 전부 제시**했으므로 placeholder 아님. "add error handling" 류 추상 지시 없음.

**3. 타입 일관성:**
- `LogLineParsed` 필드(`loggedAt/logLevel/loggerName/logMessage/rawLine`) — Task 1 정의, Task 3·4에서 동일 getter 사용 ✅
- `AdminLogFileInfo`(`fileName/fileSizeBytes/lastModifiedAt`) — Task 2 정의, Task 4·5·9 일관 ✅
- `AdminLogErrorSummary`(`exceptionClassName/occurrenceCount/lastOccurredAt/representativeMessage`) — Task 4 정의·사용 일관 ✅
- `LogFileService` 메서드명(`readRecentLines/aggregateErrors/listLogFiles/readGzLines/extractByTimeRange/getLogFileResource/getLogTotalSizeBytes/getDiskFreeBytes/getDiskTotalBytes`) — Task 5 호출과 일치 ✅
- `AdminRequest` 필드(`logLineCount/logLevelFilter/logKeyword/logErrorWithinMinutes/logFileName`) — Task 4 정의, Task 5 사용 일치 ✅

**확인 필요 항목(구현 중 검증):** `ErrorCode.INVALID_REQUEST`/`INTERNAL_SERVER_ERROR` 실제 enum 값 존재 여부 (Task 4 Step 5 노트).
