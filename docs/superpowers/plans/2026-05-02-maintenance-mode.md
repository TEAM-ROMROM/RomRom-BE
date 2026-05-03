# 서버 점검 모드 기능 구현 (#673) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 어드민 화면에서 수동으로 서버 점검 모드를 ON/OFF 할 수 있고, 점검 중에는 일반 사용자 API를 503으로 차단하며, 앱 시작 시 점검 여부/메시지/종료시간을 프론트엔드에 전달한다.

**Architecture:** Redis(SystemConfig 캐시)에 점검 설정키 3개를 저장하고, `MaintenanceFilter`가 Security Filter Chain 최상단에서 매 요청마다 Redis 값을 읽어 화이트리스트 외 요청을 차단한다. 앱 시작 시 호출하는 `/api/app/version/check` 응답(`SystemResponse`)에 점검 필드를 추가해 프론트가 즉시 판단할 수 있게 하고, 사용 중 점검 모드 전환 시에는 503 + `MAINTENANCE_MODE` ErrorCode로 전환을 감지한다.

**Tech Stack:** Spring Boot, Spring Security Filter Chain (`OncePerRequestFilter`), Redis (`SystemConfigCacheService`), Swagger (`@ApiChangeLogs`, `@Operation`)

---

## 파일 구조

### 신규 생성
- `RomRom-Web/src/main/java/com/romrom/web/filter/MaintenanceFilter.java` — 점검 모드 차단 필터

### 수정
- `RomRom-Common/src/main/java/com/romrom/common/exception/ErrorCode.java` — `MAINTENANCE_MODE` (503) 추가
- `RomRom-Common/src/main/java/com/romrom/common/dto/SystemResponse.java` — 점검 관련 필드 3개 추가
- `RomRom-Application/src/main/java/com/romrom/application/dto/AdminRequest.java` — 점검 관련 필드 3개 추가
- `RomRom-Application/src/main/java/com/romrom/application/dto/AdminResponse.java` — 점검 관련 필드 3개 추가
- `RomRom-Application/src/main/java/com/romrom/application/service/SystemConfigService.java` — `getMaintenanceConfig()`, `updateMaintenanceConfig()` 추가
- `RomRom-Application/src/main/java/com/romrom/application/service/AppConfigService.java` — `checkVersion()`에 점검 필드 포함
- `RomRom-Web/src/main/java/com/romrom/web/config/SecurityConfig.java` — `MaintenanceFilter` 등록
- `RomRom-Auth/src/main/java/com/romrom/auth/dto/SecurityUrls.java` — 점검 API를 `ADMIN_PATHS`에 추가
- `RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiController.java` — 점검 엔드포인트 2개 추가
- `RomRom-Web/src/main/java/com/romrom/web/controller/api/AppConfigControllerDocs.java` — `checkVersion()` Swagger 업데이트
- `RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiControllerDocs.java` — 없으면 생략, AdminApiController에 직접 Swagger 추가

---

## Task 1: ErrorCode에 MAINTENANCE_MODE 추가

**Files:**
- Modify: `RomRom-Common/src/main/java/com/romrom/common/exception/ErrorCode.java`

- [ ] **Step 1: `MAINTENANCE_MODE` ErrorCode 추가**

`// GLOBAL` 섹션 아래에 추가:

```java
MAINTENANCE_MODE(HttpStatus.SERVICE_UNAVAILABLE, "서버 점검 중입니다. 잠시 후 다시 시도해주세요."),
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew :RomRom-Common:compileJava
```

Expected: `BUILD SUCCESSFUL`

---

## Task 2: SystemResponse에 점검 필드 추가

**Files:**
- Modify: `RomRom-Common/src/main/java/com/romrom/common/dto/SystemResponse.java`

- [ ] **Step 1: 점검 관련 필드 3개 추가**

기존 `iosStoreUrl` 필드 아래에 추가:

```java
@Schema(description = "서버 점검 모드 활성화 여부", example = "false")
private Boolean maintenanceEnabled;

@Schema(description = "점검 안내 메시지", example = "서버 점검 중입니다. 불편을 드려 죄송합니다.")
private String maintenanceMessage;

@Schema(description = "점검 예상 종료 시간 (ISO 8601, 없으면 null)", example = "2026-05-02T15:00:00")
private String maintenanceEndTime;
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew :RomRom-Common:compileJava
```

Expected: `BUILD SUCCESSFUL`

---

## Task 3: AdminRequest / AdminResponse에 점검 필드 추가

**Files:**
- Modify: `RomRom-Application/src/main/java/com/romrom/application/dto/AdminRequest.java`
- Modify: `RomRom-Application/src/main/java/com/romrom/application/dto/AdminResponse.java`

- [ ] **Step 1: AdminRequest에 점검 필드 추가**

기존 `ugcFilterPatterns` 필드 아래에 추가:

```java
// 서버 점검 모드 관련 필드
@Schema(description = "점검 모드 활성화 여부 (\"true\"/\"false\")")
private String maintenanceEnabled;

@Schema(description = "점검 안내 메시지")
private String maintenanceMessage;

@Schema(description = "점검 예상 종료 시간 (ISO 8601, 예: 2026-05-02T15:00:00, 없으면 빈 문자열)")
private String maintenanceEndTime;
```

- [ ] **Step 2: AdminResponse에 점검 필드 추가**

기존 `ugcFilterPatterns` 필드 아래에 추가:

```java
// 서버 점검 모드 관련 필드
@Schema(description = "점검 모드 활성화 여부")
private String maintenanceEnabled;

@Schema(description = "점검 안내 메시지")
private String maintenanceMessage;

@Schema(description = "점검 예상 종료 시간 (ISO 8601)")
private String maintenanceEndTime;
```

- [ ] **Step 3: 빌드 확인**

```bash
./gradlew :RomRom-Application:compileJava
```

Expected: `BUILD SUCCESSFUL`

---

## Task 4: SystemConfigService에 점검 설정 get/update 메서드 추가

**Files:**
- Modify: `RomRom-Application/src/main/java/com/romrom/application/service/SystemConfigService.java`

- [ ] **Step 1: Redis 설정키 상수 추가**

클래스 상단 필드 선언부에 추가:

```java
private static final String KEY_MAINTENANCE_ENABLED = "server.maintenance.enabled";
private static final String KEY_MAINTENANCE_MESSAGE = "server.maintenance.message";
private static final String KEY_MAINTENANCE_END_TIME = "server.maintenance.end-time";
```

- [ ] **Step 2: `getMaintenanceConfig()` 메서드 추가**

`getUgcFilterConfig()` 메서드 아래에 추가:

```java
public AdminResponse getMaintenanceConfig() {
  return AdminResponse.builder()
      .maintenanceEnabled(systemConfigCacheService.getOrDefault(KEY_MAINTENANCE_ENABLED, "false"))
      .maintenanceMessage(systemConfigCacheService.getOrDefault(KEY_MAINTENANCE_MESSAGE, ""))
      .maintenanceEndTime(systemConfigCacheService.getOrDefault(KEY_MAINTENANCE_END_TIME, ""))
      .build();
}
```

- [ ] **Step 3: `updateMaintenanceConfig()` 메서드 추가**

`getMaintenanceConfig()` 메서드 아래에 추가:

```java
@Transactional
public AdminResponse updateMaintenanceConfig(AdminRequest adminRequest) {
  // enabled
  if (adminRequest.getMaintenanceEnabled() != null) {
    String enabledValue = adminRequest.getMaintenanceEnabled().trim();
    if (!enabledValue.equals("true") && !enabledValue.equals("false")) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    upsertMaintenanceConfig(KEY_MAINTENANCE_ENABLED, enabledValue, "서버 점검 모드 활성화 여부");
  }

  // message
  if (adminRequest.getMaintenanceMessage() != null) {
    upsertMaintenanceConfig(KEY_MAINTENANCE_MESSAGE, adminRequest.getMaintenanceMessage().trim(), "서버 점검 안내 메시지");
  }

  // end-time
  if (adminRequest.getMaintenanceEndTime() != null) {
    upsertMaintenanceConfig(KEY_MAINTENANCE_END_TIME, adminRequest.getMaintenanceEndTime().trim(), "서버 점검 예상 종료 시간");
  }

  log.info("서버 점검 모드 설정 업데이트 완료: enabled={}", adminRequest.getMaintenanceEnabled());
  return getMaintenanceConfig();
}

private void upsertMaintenanceConfig(String configKey, String configValue, String description) {
  SystemConfig maintenanceConfig = systemConfigRepository.findByConfigKey(configKey)
      .orElseGet(() -> SystemConfig.builder().configKey(configKey).description(description).build());
  maintenanceConfig.setConfigValue(configValue);
  systemConfigRepository.save(maintenanceConfig);
  systemConfigCacheService.put(configKey, configValue);
}
```

- [ ] **Step 4: 빌드 확인**

```bash
./gradlew :RomRom-Application:compileJava
```

Expected: `BUILD SUCCESSFUL`

---

## Task 5: AppConfigService.checkVersion()에 점검 필드 포함

**Files:**
- Modify: `RomRom-Application/src/main/java/com/romrom/application/service/AppConfigService.java`

- [ ] **Step 1: 점검 설정키 상수 추가**

기존 `KEY_STORE_IOS` 상수 아래에 추가:

```java
private static final String KEY_MAINTENANCE_ENABLED = "server.maintenance.enabled";
private static final String KEY_MAINTENANCE_MESSAGE = "server.maintenance.message";
private static final String KEY_MAINTENANCE_END_TIME = "server.maintenance.end-time";
```

- [ ] **Step 2: `checkVersion()` 반환값에 점검 필드 추가**

기존 `checkVersion()` 메서드를:

```java
@Transactional(readOnly = true)
public SystemResponse checkVersion() {
  String maintenanceEnabledValue = getConfigValue(KEY_MAINTENANCE_ENABLED);
  return SystemResponse.builder()
      .minimumVersion(getConfigValue(KEY_MIN_VERSION))
      .latestVersion(getConfigValue(KEY_LATEST_VERSION))
      .androidStoreUrl(getConfigValue(KEY_STORE_ANDROID))
      .iosStoreUrl(getConfigValue(KEY_STORE_IOS))
      .maintenanceEnabled("true".equals(maintenanceEnabledValue))
      .maintenanceMessage(getConfigValue(KEY_MAINTENANCE_MESSAGE))
      .maintenanceEndTime(getConfigValue(KEY_MAINTENANCE_END_TIME))
      .build();
}
```

- [ ] **Step 3: 빌드 확인**

```bash
./gradlew :RomRom-Application:compileJava
```

Expected: `BUILD SUCCESSFUL`

---

## Task 6: MaintenanceFilter 구현

**Files:**
- Create: `RomRom-Web/src/main/java/com/romrom/web/filter/MaintenanceFilter.java`

- [ ] **Step 1: MaintenanceFilter 클래스 생성**

```java
package com.romrom.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.exception.ErrorResponse;
import com.romrom.common.service.SystemConfigCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class MaintenanceFilter extends OncePerRequestFilter {

  // 점검 모드에서도 통과시킬 경로 (어드민, 버전체크, 헬스체크)
  private static final List<String> MAINTENANCE_WHITELIST = List.of(
      "/api/admin",
      "/api/app/version/check",
      "/actuator",
      "/admin"
  );

  private final SystemConfigCacheService systemConfigCacheService;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    String maintenanceEnabled = systemConfigCacheService.getOrDefault("server.maintenance.enabled", "false");

    if ("true".equals(maintenanceEnabled) && !isWhitelisted(request.getRequestURI())) {
      log.info("점검 모드 차단: {}", request.getRequestURI());
      sendMaintenanceResponse(response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private boolean isWhitelisted(String requestUri) {
    return MAINTENANCE_WHITELIST.stream().anyMatch(requestUri::startsWith);
  }

  private void sendMaintenanceResponse(HttpServletResponse response) throws IOException {
    response.setStatus(ErrorCode.MAINTENANCE_MODE.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    ErrorResponse errorResponse = ErrorResponse.builder()
        .errorCode(ErrorCode.MAINTENANCE_MODE)
        .errorMessage(ErrorCode.MAINTENANCE_MODE.getMessage())
        .build();

    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
  }
}
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew :RomRom-Web:compileJava
```

Expected: `BUILD SUCCESSFUL`

---

## Task 7: SecurityConfig에 MaintenanceFilter 등록

**Files:**
- Modify: `RomRom-Web/src/main/java/com/romrom/web/config/SecurityConfig.java`

- [ ] **Step 1: ObjectMapper 의존성 주입 필드 추가**

클래스 필드 선언부에 추가:

```java
private final ObjectMapper objectMapper;
```

- [ ] **Step 2: MaintenanceFilter를 Filter Chain 최상단에 추가**

`.addFilterBefore(new AdminJwtAuthenticationFilter(jwtUtil), TokenAuthenticationFilter.class)` 바로 앞에 추가:

```java
.addFilterBefore(
    new MaintenanceFilter(systemConfigCacheService, objectMapper),
    AdminJwtAuthenticationFilter.class
)
```

- [ ] **Step 3: SystemConfigCacheService 의존성 주입 필드 추가**

```java
private final SystemConfigCacheService systemConfigCacheService;
```

- [ ] **Step 4: import 추가**

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.common.service.SystemConfigCacheService;
import com.romrom.web.filter.MaintenanceFilter;
```

- [ ] **Step 5: 빌드 확인**

```bash
./gradlew :RomRom-Web:compileJava
```

Expected: `BUILD SUCCESSFUL`

---

## Task 8: SecurityUrls에 점검 API 경로 추가

**Files:**
- Modify: `RomRom-Auth/src/main/java/com/romrom/auth/dto/SecurityUrls.java`

- [ ] **Step 1: ADMIN_PATHS에 점검 API 경로 2개 추가**

기존 `"/api/admin/alert-config/update"` 아래에 추가:

```java
// Admin APIs - Maintenance Config
"/api/admin/config/maintenance/get",
"/api/admin/config/maintenance/update",

// Admin APIs - Members (추가 누락분)
"/api/admin/members/suspend",
"/api/admin/members/unsuspend",
"/api/admin/members/sanction-history",
"/api/admin/sanctions/history"
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew :RomRom-Auth:compileJava
```

Expected: `BUILD SUCCESSFUL`

---

## Task 9: AdminApiController에 점검 엔드포인트 추가

**Files:**
- Modify: `RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiController.java`

- [ ] **Step 1: 점검 설정 조회 엔드포인트 추가**

기존 `/config/ugc-filter/update` 엔드포인트 아래에 추가:

```java
@PostMapping(value = "/config/maintenance/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@LogMonitor
public ResponseEntity<AdminResponse> getMaintenanceConfig(@ModelAttribute AdminRequest request) {
    return ResponseEntity.ok(systemConfigService.getMaintenanceConfig());
}

@PostMapping(value = "/config/maintenance/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@LogMonitor
public ResponseEntity<AdminResponse> updateMaintenanceConfig(@ModelAttribute AdminRequest request) {
    return ResponseEntity.ok(systemConfigService.updateMaintenanceConfig(request));
}
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew :RomRom-Web:compileJava
```

Expected: `BUILD SUCCESSFUL`

---

## Task 10: Swagger 문서 업데이트

**Files:**
- Modify: `RomRom-Web/src/main/java/com/romrom/web/controller/api/AppConfigControllerDocs.java`
- Modify: `RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiController.java`

- [ ] **Step 1: AppConfigControllerDocs의 checkVersion() ApiChangeLog 추가**

기존 `@ApiChangeLogs` 배열 **최상단에** 추가:

```java
@ApiChangeLog(date = "2026.05.02", author = Author.SUHSAECHAN, issueNumber = 673, description = "점검 모드 필드 추가 - maintenanceEnabled, maintenanceMessage, maintenanceEndTime 반환"),
```

- [ ] **Step 2: checkVersion() @Operation description 업데이트**

기존 반환값 설명 아래에 추가:

```
- **`maintenanceEnabled`**: 서버 점검 모드 활성화 여부 (true면 점검 화면 표시)
- **`maintenanceMessage`**: 점검 안내 메시지
- **`maintenanceEndTime`**: 점검 예상 종료 시간 (ISO 8601, 없으면 빈 문자열)
```

- [ ] **Step 3: AdminApiController에 점검 엔드포인트 Swagger 추가**

`getMaintenanceConfig()` 메서드 위에:

```java
@ApiChangeLogs({
    @ApiChangeLog(date = "2026.05.02", author = Author.SUHSAECHAN, issueNumber = 673, description = "서버 점검 모드 조회 API 구현"),
})
@Operation(
    summary = "서버 점검 모드 설정 조회",
    description = """
    ## 인증: **ROLE_ADMIN**

    ## 반환값 (AdminResponse)
    - **`maintenanceEnabled`**: 점검 모드 활성화 여부 ("true"/"false")
    - **`maintenanceMessage`**: 점검 안내 메시지
    - **`maintenanceEndTime`**: 점검 예상 종료 시간 (ISO 8601, 없으면 빈 문자열)
    """
)
```

`updateMaintenanceConfig()` 메서드 위에:

```java
@ApiChangeLogs({
    @ApiChangeLog(date = "2026.05.02", author = Author.SUHSAECHAN, issueNumber = 673, description = "서버 점검 모드 업데이트 API 구현"),
})
@Operation(
    summary = "서버 점검 모드 설정 업데이트",
    description = """
    ## 인증: **ROLE_ADMIN**

    ## 요청 파라미터 (multipart/form-data)
    - **`maintenanceEnabled`** (String, 선택): "true" 또는 "false"
    - **`maintenanceMessage`** (String, 선택): 점검 안내 메시지
    - **`maintenanceEndTime`** (String, 선택): 점검 예상 종료 시간 (ISO 8601, 예: 2026-05-02T15:00:00)

    ## 동작 설명
    - null인 필드는 무시하고 기존 설정 유지
    - maintenanceEnabled를 "true"로 설정하면 즉시 점검 모드 활성화
    - 점검 중에는 /api/admin/**, /api/app/version/check, /actuator/** 외 모든 API 503 반환

    ## 에러코드
    - INVALID_REQUEST (400): maintenanceEnabled가 "true"/"false" 외의 값
    """
)
```

- [ ] **Step 4: 전체 빌드 확인**

```bash
./gradlew :RomRom-Web:compileJava
```

Expected: `BUILD SUCCESSFUL`

---

## Task 11: 전체 빌드 및 동작 검증

- [ ] **Step 1: 전체 빌드**

```bash
./gradlew build -x test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 서버 기동 후 점검 모드 활성화 테스트**

1. 어드민 로그인 후 `POST /api/admin/config/maintenance/update` 호출:
   ```
   maintenanceEnabled=true
   maintenanceMessage=서버 점검 중입니다. 불편을 드려 죄송합니다.
   maintenanceEndTime=2026-05-02T23:00:00
   ```
2. 일반 API(`POST /api/item/list` 등) 호출 → 503 + `MAINTENANCE_MODE` 응답 확인
3. `POST /api/app/version/check` 호출 → 통과 + `maintenanceEnabled: true` 응답 확인
4. `POST /api/admin/config/maintenance/get` 호출 → 통과 + 설정 조회 확인
5. `POST /api/admin/config/maintenance/update` with `maintenanceEnabled=false` → 점검 해제 확인
6. 점검 해제 후 일반 API 정상 응답 확인

- [ ] **Step 3: 서버 재시작 시 Redis 캐시 유지 확인**

서버 재시작 후 `getMaintenanceConfig()` 호출 → 기존 설정값이 DB → Redis 로딩으로 유지되는지 확인 (`onApplicationReady()`의 `loadAllToRedis()`가 처리)
