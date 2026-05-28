# 이미지 조건부 압축 + 업로드 병렬화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** FE 압축본(WebP·소용량)은 BE 재압축 스킵, 원본/구버전만 압축. 업로드 루프 병렬화. 임계치·풀크기 DB 동적 설정.

**Architecture:** `ImageCompressionService`에 Redis 설정 기반 스킵 가드 추가(null 반환 시 기존 원본 저장 fallback 재활용). `StorageService.saveImages`를 전용 `imageUploadExecutor` + `CompletableFuture`로 병렬화(순서 보장). `image.*` SystemConfig 3키 + Admin get/update + Flyway seed.

**Tech Stack:** Spring Boot 멀티모듈, scrimage-webp, MinIO/FTP, PostgreSQL+Flyway, Redis(SystemConfigCacheService).

---

## 파일 구조

- Modify: `RomRom-Domain-Storage/.../service/ImageCompressionService.java` — 스킵 가드 + 로그
- Create: `RomRom-Domain-Storage/.../config/ImageUploadExecutorConfig.java` — 전용 스레드풀 Bean
- Modify: `RomRom-Domain-Storage/.../service/StorageService.java` — saveImages 병렬화
- Modify: `RomRom-Application/.../dto/AdminRequest.java` — image 설정 필드 3개
- Modify: `RomRom-Application/.../dto/AdminResponse.java` — image 설정 필드 3개
- Modify: `RomRom-Application/.../service/SystemConfigService.java` — getImageConfig/updateImageConfig
- Modify: `RomRom-Web/.../controller/api/AdminApiController.java` — /config/image/get·update + @ApiChangeLog
- Create: `RomRom-Web/src/main/resources/db/migration/V1_4_61__seed_image_compress_config.sql`
- Modify: `RomRom-Common/.../service/ImageCompressionServiceTest.java` — 스킵 가드 테스트 추가

설정 키 상수:
- `image.compress.skip-content-type` 기본 `image/webp`
- `image.compress.skip-max-size-bytes` 기본 `512000`
- `image.upload.parallel-pool-size` 기본 `8`

---

## Task 1: Flyway seed 마이그레이션

**Files:**
- Create: `RomRom-Web/src/main/resources/db/migration/V1_4_61__seed_image_compress_config.sql`

- [ ] **Step 1: 마이그레이션 작성** (멱등 블록, ON CONFLICT DO NOTHING)

```sql
-- 이미지 조건부 압축/업로드 병렬화 설정 초기값
-- V1.4.61: #733 FE 클라이언트 압축 대응 (BE 조건부 압축 + 업로드 병렬화)

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'system_config'
    ) THEN
        INSERT INTO system_config (config_key, config_value, description)
        VALUES
            ('image.compress.skip-content-type', 'image/webp', '압축 스킵 대상 contentType'),
            ('image.compress.skip-max-size-bytes', '512000', '이 용량(byte) 이하이고 스킵 contentType이면 압축 스킵'),
            ('image.upload.parallel-pool-size', '8', '이미지 업로드 병렬 스레드풀 크기 (서버 재시작 시 반영)')
        ON CONFLICT (config_key) DO NOTHING;

        RAISE NOTICE '이미지 압축/업로드 설정 초기 데이터 INSERT 완료';
    ELSE
        RAISE NOTICE 'system_config 테이블이 존재하지 않아 이미지 설정 INSERT를 건너뜁니다.';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '이미지 설정 마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
```

- [ ] **Step 2: 커밋**

```bash
git add RomRom-Web/src/main/resources/db/migration/V1_4_61__seed_image_compress_config.sql
```
(전체 작업 후 일괄 커밋 — 본 plan은 단일 커밋 전략)

---

## Task 2: ImageCompressionService 조건부 압축 가드

**Files:**
- Modify: `RomRom-Domain-Storage/src/main/java/com/romrom/storage/service/ImageCompressionService.java`

- [ ] **Step 1: SystemConfigCacheService 주입 + 키 상수 추가**

클래스 필드/상수:
```java
private static final String KEY_SKIP_CONTENT_TYPE = "image.compress.skip-content-type";
private static final String KEY_SKIP_MAX_SIZE_BYTES = "image.compress.skip-max-size-bytes";
private static final String DEFAULT_SKIP_CONTENT_TYPE = "image/webp";
private static final String DEFAULT_SKIP_MAX_SIZE_BYTES = "512000";

private final SystemConfigCacheService systemConfigCacheService;
```
(`@RequiredArgsConstructor` 이미 존재 → 생성자 자동)

import 추가: `com.romrom.common.service.SystemConfigCacheService`, `org.springframework.web.multipart.MultipartFile`(이미 있음).

- [ ] **Step 2: shouldSkipCompression 메서드 추가**

```java
/**
 * 압축 스킵 여부 판단
 * FE 압축본(WebP + 소용량)은 재압축 불필요 → 디코드 없이 contentType/size만 검사
 */
private boolean shouldSkipCompression(MultipartFile file) {
  String skipContentType = systemConfigCacheService.getOrDefault(KEY_SKIP_CONTENT_TYPE, DEFAULT_SKIP_CONTENT_TYPE);
  long skipMaxSizeBytes = parseSkipMaxSizeBytes();
  String requestContentType = file.getContentType();
  return requestContentType != null
      && requestContentType.equalsIgnoreCase(skipContentType)
      && file.getSize() <= skipMaxSizeBytes;
}

private long parseSkipMaxSizeBytes() {
  String rawSkipMaxSize = systemConfigCacheService.getOrDefault(KEY_SKIP_MAX_SIZE_BYTES, DEFAULT_SKIP_MAX_SIZE_BYTES);
  try {
    return Long.parseLong(rawSkipMaxSize.trim());
  } catch (NumberFormatException e) {
    log.warn("image.compress.skip-max-size-bytes 파싱 실패, 기본값 사용: {}", rawSkipMaxSize);
    return Long.parseLong(DEFAULT_SKIP_MAX_SIZE_BYTES);
  }
}
```

- [ ] **Step 3: compress() 진입부에 가드 추가**

`compress(MultipartFile file)` 의 `try {` 직후 첫 줄로 삽입:
```java
if (shouldSkipCompression(file)) {
  log.info("이미지 압축 스킵(FE 압축본 추정): {}, 크기: {} bytes, contentType: {}",
      file.getOriginalFilename(), file.getSize(), file.getContentType());
  return null;
}
```
(null 반환 → StorageService가 기존 `uploadOriginal` 경로로 원본 저장. 추가 변경 불필요.)

- [ ] **Step 4: 컴파일 확인** (사용자 환경)

Run: `./gradlew :RomRom-Domain-Storage:compileJava`
Expected: BUILD SUCCESSFUL

---

## Task 3: 전용 스레드풀 Bean

**Files:**
- Create: `RomRom-Domain-Storage/src/main/java/com/romrom/storage/config/ImageUploadExecutorConfig.java`

- [ ] **Step 1: Config 클래스 생성**

```java
package com.romrom.storage.config;

import com.romrom.common.service.SystemConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ImageUploadExecutorConfig {

  private static final String KEY_PARALLEL_POOL_SIZE = "image.upload.parallel-pool-size";
  private static final String DEFAULT_PARALLEL_POOL_SIZE = "8";

  private final SystemConfigCacheService systemConfigCacheService;

  @Bean(name = "imageUploadExecutor", destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor imageUploadExecutor() {
    int poolSize = resolvePoolSize();
    ThreadPoolTaskExecutor imageUploadExecutor = new ThreadPoolTaskExecutor();
    imageUploadExecutor.setCorePoolSize(poolSize);
    imageUploadExecutor.setMaxPoolSize(poolSize);
    imageUploadExecutor.setQueueCapacity(100);
    imageUploadExecutor.setThreadNamePrefix("img-upload-");
    imageUploadExecutor.initialize();
    log.info("이미지 업로드 스레드풀 초기화: poolSize={}", poolSize);
    return imageUploadExecutor;
  }

  private int resolvePoolSize() {
    String rawPoolSize = systemConfigCacheService.getOrDefault(KEY_PARALLEL_POOL_SIZE, DEFAULT_PARALLEL_POOL_SIZE);
    try {
      int parsedPoolSize = Integer.parseInt(rawPoolSize.trim());
      return parsedPoolSize > 0 ? parsedPoolSize : Integer.parseInt(DEFAULT_PARALLEL_POOL_SIZE);
    } catch (NumberFormatException e) {
      log.warn("image.upload.parallel-pool-size 파싱 실패, 기본값 사용: {}", rawPoolSize);
      return Integer.parseInt(DEFAULT_PARALLEL_POOL_SIZE);
    }
  }
}
```

주의: Bean 생성은 `ApplicationReadyEvent`(SystemConfig Redis 로딩)보다 먼저 일어날 수 있음 → Redis 미로딩 시 `getOrDefault`가 기본값 8 반환. 정상 동작(재시작 반영 정책과 일치).

- [ ] **Step 2: 컴파일 확인** (사용자 환경)

Run: `./gradlew :RomRom-Domain-Storage:compileJava`
Expected: BUILD SUCCESSFUL

---

## Task 4: StorageService 병렬화

**Files:**
- Modify: `RomRom-Domain-Storage/src/main/java/com/romrom/storage/service/StorageService.java`

- [ ] **Step 1: Executor 주입**

import 추가:
```java
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
```

필드 + 생성자 파라미터 추가:
```java
private final ThreadPoolTaskExecutor imageUploadExecutor;
```
생성자에 `@Qualifier("imageUploadExecutor") ThreadPoolTaskExecutor imageUploadExecutor` 추가, `this.imageUploadExecutor = imageUploadExecutor;` 할당.

- [ ] **Step 2: saveImages 병렬화**

기존:
```java
for (MultipartFile file : itemImageFiles) {
  String imageUrl = uploadWithFallback(file);
  imageUrls.add(imageUrl);
}
```
교체:
```java
List<CompletableFuture<String>> uploadFutures = new ArrayList<>();
for (MultipartFile file : itemImageFiles) {
  uploadFutures.add(CompletableFuture.supplyAsync(() -> uploadWithFallback(file), imageUploadExecutor));
}
for (CompletableFuture<String> uploadFuture : uploadFutures) {
  imageUrls.add(uploadFuture.join());  // 입력 순서 보장
}
```
(`imageUrls` 선언 `List<String> imageUrls = new ArrayList<>();` 유지.)

- [ ] **Step 3: 컴파일 확인** (사용자 환경)

Run: `./gradlew :RomRom-Domain-Storage:compileJava`
Expected: BUILD SUCCESSFUL

---

## Task 5: AdminRequest/AdminResponse 필드

**Files:**
- Modify: `RomRom-Application/src/main/java/com/romrom/application/dto/AdminRequest.java`
- Modify: `RomRom-Application/src/main/java/com/romrom/application/dto/AdminResponse.java`

- [ ] **Step 1: AdminRequest 필드 3개 추가** (maintenanceEndTime 필드 다음, 동일 스타일)

```java
    // 이미지 압축/업로드 설정 관련 필드
    @Schema(description = "압축 스킵 대상 contentType (예: image/webp)")
    private String imageCompressSkipContentType;

    @Schema(description = "압축 스킵 최대 용량(byte). 이 이하이고 스킵 contentType이면 압축 생략")
    private String imageCompressSkipMaxSizeBytes;

    @Schema(description = "이미지 업로드 병렬 스레드풀 크기 (서버 재시작 시 반영)")
    private String imageUploadParallelPoolSize;
```

- [ ] **Step 2: AdminResponse 동일 필드 3개 추가** (maintenance 응답 필드 근처, AdminResponse 스타일 따름)

```java
    @Schema(description = "압축 스킵 대상 contentType")
    private String imageCompressSkipContentType;

    @Schema(description = "압축 스킵 최대 용량(byte)")
    private String imageCompressSkipMaxSizeBytes;

    @Schema(description = "이미지 업로드 병렬 스레드풀 크기")
    private String imageUploadParallelPoolSize;
```
(AdminResponse 실제 어노테이션/구조 확인 후 동일 패턴 적용 — `@Builder` + `@Schema` 사용 중.)

- [ ] **Step 3: 컴파일 확인** (사용자 환경)

Run: `./gradlew :RomRom-Application:compileJava`
Expected: BUILD SUCCESSFUL

---

## Task 6: SystemConfigService get/update

**Files:**
- Modify: `RomRom-Application/src/main/java/com/romrom/application/service/SystemConfigService.java`

- [ ] **Step 1: 키 상수 추가** (기존 KEY_MAINTENANCE_* 근처)

```java
  private static final String KEY_IMAGE_SKIP_CONTENT_TYPE = "image.compress.skip-content-type";
  private static final String KEY_IMAGE_SKIP_MAX_SIZE_BYTES = "image.compress.skip-max-size-bytes";
  private static final String KEY_IMAGE_UPLOAD_POOL_SIZE = "image.upload.parallel-pool-size";
```

- [ ] **Step 2: getImageConfig 추가** (getMaintenanceConfig 패턴)

```java
  public AdminResponse getImageConfig() {
    return AdminResponse.builder()
        .imageCompressSkipContentType(systemConfigCacheService.getOrDefault(KEY_IMAGE_SKIP_CONTENT_TYPE, "image/webp"))
        .imageCompressSkipMaxSizeBytes(systemConfigCacheService.getOrDefault(KEY_IMAGE_SKIP_MAX_SIZE_BYTES, "512000"))
        .imageUploadParallelPoolSize(systemConfigCacheService.getOrDefault(KEY_IMAGE_UPLOAD_POOL_SIZE, "8"))
        .build();
  }
```

- [ ] **Step 3: updateImageConfig 추가** (PATCH 방식, upsertMaintenanceConfig 재사용)

```java
  @Transactional
  public AdminResponse updateImageConfig(AdminRequest adminRequest) {
    if (adminRequest.getImageCompressSkipContentType() != null) {
      upsertMaintenanceConfig(KEY_IMAGE_SKIP_CONTENT_TYPE,
          adminRequest.getImageCompressSkipContentType().trim(), "압축 스킵 대상 contentType");
    }
    if (adminRequest.getImageCompressSkipMaxSizeBytes() != null) {
      String skipMaxSizeValue = adminRequest.getImageCompressSkipMaxSizeBytes().trim();
      if (!skipMaxSizeValue.isEmpty()) {
        validateNonNegativeLong(skipMaxSizeValue);
        upsertMaintenanceConfig(KEY_IMAGE_SKIP_MAX_SIZE_BYTES, skipMaxSizeValue, "압축 스킵 최대 용량(byte)");
      }
    }
    if (adminRequest.getImageUploadParallelPoolSize() != null) {
      String poolSizeValue = adminRequest.getImageUploadParallelPoolSize().trim();
      if (!poolSizeValue.isEmpty()) {
        validatePositiveInt(poolSizeValue);
        upsertMaintenanceConfig(KEY_IMAGE_UPLOAD_POOL_SIZE, poolSizeValue, "이미지 업로드 병렬 스레드풀 크기 (서버 재시작 시 반영)");
      }
    }
    log.info("이미지 압축/업로드 설정 업데이트 완료");
    return getImageConfig();
  }

  private void validateNonNegativeLong(String value) {
    try {
      if (Long.parseLong(value) < 0) {
        throw new CustomException(ErrorCode.INVALID_REQUEST);
      }
    } catch (NumberFormatException e) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
  }

  private void validatePositiveInt(String value) {
    try {
      if (Integer.parseInt(value) <= 0) {
        throw new CustomException(ErrorCode.INVALID_REQUEST);
      }
    } catch (NumberFormatException e) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
  }
```
(`upsertMaintenanceConfig`는 범용 single-key upsert이므로 이름 무관하게 재사용. CustomException/ErrorCode 이미 import됨.)

- [ ] **Step 4: 컴파일 확인** (사용자 환경)

Run: `./gradlew :RomRom-Application:compileJava`
Expected: BUILD SUCCESSFUL

---

## Task 7: AdminApiController 엔드포인트

**Files:**
- Modify: `RomRom-Web/src/main/java/com/romrom/web/controller/api/AdminApiController.java`

- [ ] **Step 1: get/update 엔드포인트 추가** (maintenance 엔드포인트 다음)

```java
    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.28", author = Author.SUHSAECHAN, issueNumber = 733, description = "이미지 조건부 압축/업로드 병렬화 설정 조회 API 구현"),
    })
    @Operation(
        summary = "이미지 압축/업로드 설정 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 반환값 (AdminResponse)
        - **`imageCompressSkipContentType`**: 압축 스킵 대상 contentType (기본 image/webp)
        - **`imageCompressSkipMaxSizeBytes`**: 압축 스킵 최대 용량(byte, 기본 512000)
        - **`imageUploadParallelPoolSize`**: 업로드 병렬 스레드풀 크기 (기본 8, 재시작 반영)
        """
    )
    @PostMapping(value = "/config/image/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getImageConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getImageConfig());
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.28", author = Author.SUHSAECHAN, issueNumber = 733, description = "이미지 조건부 압축/업로드 병렬화 설정 업데이트 API 구현"),
    })
    @Operation(
        summary = "이미지 압축/업로드 설정 업데이트",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data, 모두 선택)
        - **`imageCompressSkipContentType`** (String): 압축 스킵 대상 contentType
        - **`imageCompressSkipMaxSizeBytes`** (String): 압축 스킵 최대 용량(byte, 0 이상 정수)
        - **`imageUploadParallelPoolSize`** (String): 업로드 병렬 스레드풀 크기 (양의 정수, 재시작 반영)

        ## 동작 설명
        - null/빈 필드는 무시하고 기존 설정 유지
        - contentType/용량 변경은 Redis 캐시 즉시 반영, 풀 크기는 서버 재시작 시 반영

        ## 에러코드
        - INVALID_REQUEST (400): skipMaxSizeBytes가 음수/비정수, poolSize가 0이하/비정수
        """
    )
    @PostMapping(value = "/config/image/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateImageConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateImageConfig(request));
    }
```

- [ ] **Step 2: 컴파일 확인** (사용자 환경)

Run: `./gradlew :RomRom-Web:compileJava`
Expected: BUILD SUCCESSFUL

---

## Task 8: 테스트 — 조건부 가드

**Files:**
- Modify: `RomRom-Common/src/test/java/com/romrom/common/service/ImageCompressionServiceTest.java`

- [ ] **Step 1: WebP 소용량 스킵 테스트 추가** (mainTest에 timeLog 추가 + 메서드)

mainTest 내부:
```java
    lineLog(null);
    timeLog(this::imageCompressionService_WebP_소용량_스킵_테스트);
    lineLog(null);
```

신규 메서드:
```java
  public void imageCompressionService_WebP_소용량_스킵_테스트() {
    // image/webp + 소용량 MockMultipartFile (실제 webp 디코드 불필요 — 가드는 contentType/size만 검사)
    byte[] tinyWebpBytes = new byte[1024]; // 1KB
    MultipartFile webpFile = new MockMultipartFile("file", "test.webp", "image/webp", tinyWebpBytes);

    CompressedImage compressed = imageCompressionService.compress(webpFile);

    // 스킵 → null 반환 (원본 저장 경로)
    if (compressed == null) {
      superLog("결과", "WebP 소용량 → 압축 스킵(null) 정상");
    } else {
      superLog("결과", "스킵 실패 — 압축됨: " + compressed.getFileName());
      throw new IllegalStateException("WebP 소용량은 압축 스킵되어야 함");
    }
  }
```

import 확인: `MockMultipartFile`, `CompressedImage`, `MultipartFile` 이미 존재.

- [ ] **Step 2: 테스트 실행** (사용자 환경 — Redis 필요)

Run: `./gradlew :RomRom-Common:test --tests "*ImageCompressionServiceTest*"`
Expected: PASS (스킵 가드 동작 확인). Redis 미설정 시 기본값으로 스킵 동작.

---

## Task 9: 일괄 커밋 + 푸시

- [ ] **Step 1: 전체 변경 add + 커밋** (RomRom 커밋 컨벤션, main 브랜치 작업)

```bash
git add RomRom-Domain-Storage RomRom-Application RomRom-Web RomRom-Common docs/superpowers
git commit -m "feat: 이미지 조건부 압축 가드 및 업로드 병렬화 (#733)"
```

- [ ] **Step 2: 푸시**

```bash
git push origin main
```

---

## Self-Review 결과

- 스펙 커버리지: 작업항목1(가드)=Task2, 작업항목2(병렬화)=Task3·4, 작업항목3(로그)=Task2 Step3, 동적설정=Task1·5·6·7. 전부 매핑됨.
- placeholder: AdminResponse 실제 구조는 구현 시 Read 후 동일 패턴 적용(명시). 그 외 전부 실코드.
- 타입 일관성: configKey 문자열 3개 일관, AdminRequest/Response 필드명 일치(imageCompressSkipContentType/imageCompressSkipMaxSizeBytes/imageUploadParallelPoolSize), `imageUploadExecutor` Bean명 일관.
