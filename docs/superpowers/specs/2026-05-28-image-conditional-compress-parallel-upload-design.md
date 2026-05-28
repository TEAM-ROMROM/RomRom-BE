# 이슈 #733 — FE 클라이언트 압축 대응: BE 조건부 압축 + 업로드 루프 병렬화

- 이슈: https://github.com/TEAM-ROMROM/RomRom-BE/issues/733
- 관련 FE 이슈: TEAM-ROMROM/RomRom-FE#887
- 작성일: 2026-05-28

## 배경

`POST /api/image/upload` 는 업로드된 모든 이미지를 동기 순차로 WebP 압축한다(`ImageCompressionService`, scrimage-webp→네이티브 cwebp). `StorageService.saveImages` 의 for 루프가 이미지 N장을 직렬 처리(압축+MinIO 업로드)하여 응답이 느리다.

FE 가 업로드 전에 WebP 압축(가로 1280 축소, Q80)을 수행하도록 변경 중이다(RomRom-FE #887). FE 압축본은 이미 작고 WebP 이므로, BE 가 다시 압축하면 불필요한 cwebp 호출이 발생한다.

## 목표

FE 가 보낸 압축본은 빠르게 통과시키고, **구버전 앱·원본 fallback 만** BE 가 압축하는 하이브리드로 전환한다. 동시에 업로드 루프를 병렬화해 안전망 압축 경로의 지연을 완화한다.

## 설계 결정 (확정)

| 항목 | 결정 | 비고 |
|---|---|---|
| 압축 스킵 가드 | `contentType == image/webp && file.getSize() <= 임계치` | 디코드 0회. 가로 픽셀 검사 안 함 |
| 용량 임계치 | 500KB (512000 bytes) 기본 | DB 동적 설정 |
| 병렬화 방식 | 전용 ThreadPool + CompletableFuture | `parallelStream`(commonPool 경합) / `@Async`(self-invocation 제약) 대신 |
| 설정 저장소 | `SystemConfig` DB + Redis 캐시, Admin 페이지에서 수정 | 기존 maintenance/ugc 패턴 재사용 |
| 임계치 반영 시점 | 런타임 즉시 (Redis `getOrDefault`) | |
| 스레드풀 크기 반영 시점 | 서버 재시작 | 동적 리사이즈는 과설계로 제외 |

## 컴포넌트별 설계

### A. 동적 설정 — `image.*` SystemConfig 키

신규 configKey 3개:

| configKey | 기본값 | description |
|---|---|---|
| `image.compress.skip-content-type` | `image/webp` | 압축 스킵 대상 contentType |
| `image.compress.skip-max-size-bytes` | `512000` | 이 용량 이하 + 스킵 contentType 이면 압축 스킵 |
| `image.upload.parallel-pool-size` | `8` | 업로드 병렬 스레드풀 크기 (재시작 반영). I/O 바운드(MinIO/FTP) 기준 코어×2~4, 요청당 이미지 ≤10장 가정 |

변경:
- `SystemConfigService` 에 `getImageConfig()` / `updateImageConfig(AdminRequest)` 추가 — 기존 `updateMaintenanceConfig` upsert 패턴(`findByConfigKey().orElseGet(...)` → `setConfigValue` → `save` → `systemConfigCacheService.put`)과 동일.
- `AdminRequest` / `AdminResponse` 에 필드 3개 추가 (Admin DTO 단일 컨벤션 — 도메인별 DTO 금지).
  - 필드명: `imageCompressSkipContentType`, `imageCompressSkipMaxSizeBytes`, `imageUploadParallelPoolSize` (전부 String — 기존 config 필드 전부 String).
- `AdminApiController` 에 `/config/image/get`, `/config/image/update` 추가 (POST, `multipart/form-data`, `@ModelAttribute`).
  - `AdminApiControllerDocs` (또는 해당 Docs 인터페이스)에 `@Operation` + `@ApiChangeLog` 최상단 추가.
- Flyway 마이그레이션 `V{next}__seed_image_compress_config.sql`: 키 3개 seed insert. `system_config` 테이블 존재 체크 + 키 미존재 시에만 insert, `DO $$ ... EXCEPTION WHEN OTHERS THEN RAISE WARNING ... END $$;` 멱등 블록.

검증:
- `image.upload.parallel-pool-size` 는 양의 정수 형식 검증 (숫자 아니거나 ≤0 이면 `INVALID_REQUEST`).
- `image.compress.skip-max-size-bytes` 는 0 이상 정수 검증.
- 빈 값은 기존 설정 유지(기존 update 패턴과 동일).

### B. 조건부 압축 가드 — `ImageCompressionService`

`compress(MultipartFile)` 진입부에 스킵 가드 추가:

```
compress(file):
  if shouldSkipCompression(file):
     log.info("이미지 압축 스킵: {} ({} bytes, contentType={})", ...)
     return null            // null = 원본 그대로 저장 (기존 fallback 경로 재사용)
  ... 기존 압축 로직 (디코드 + 리사이즈 + cwebp) ...
  log.info("이미지 압축 수행 완료: ... 압축률 {}%", ...)   // 기존 로그 유지

shouldSkipCompression(file):
  skipContentType = cache.getOrDefault("image.compress.skip-content-type", "image/webp")
  skipMaxSize     = parseLong(cache.getOrDefault("image.compress.skip-max-size-bytes", "512000"))
  return skipContentType.equalsIgnoreCase(file.getContentType())
         && file.getSize() <= skipMaxSize
```

- `ImageCompressionService` 에 `SystemConfigCacheService` 주입 (Domain-Storage → api(RomRom-Common) 이미 의존, 추가 의존성 없음).
- `compress()` 가 `null` 반환 → `StorageService.uploadWithFallback` 이 이미 `uploadOriginal` 경로를 타므로 **StorageService 압축 분기는 변경 불필요** (기존 fallback 재활용).
- 작업항목 3(압축 스킵/수행 로그)은 이 가드의 `log.info` 두 갈래로 충족.
- 설정 파싱 실패(숫자 깨짐) 시 기본값 fallback — 압축 경로가 안전망이므로 스킵 오판이 나도 "압축 수행"으로 떨어지는 게 안전.

### C. 업로드 루프 병렬화 — `StorageService.saveImages` + 전용 Executor

신규 Executor Bean (Domain-Storage `@Configuration`):

```java
@Bean("imageUploadExecutor")
ThreadPoolTaskExecutor imageUploadExecutor(SystemConfigCacheService cache) {
    int poolSize = parseInt(cache.getOrDefault("image.upload.parallel-pool-size", "8"));
    // corePoolSize = maxPoolSize = poolSize, queueCapacity, threadNamePrefix="img-upload-"
    // 부팅 시 1회 결정 (재시작 반영)
}
```

`saveImages` 변경:

```java
List<CompletableFuture<String>> futures = itemImageFiles.stream()
    .map(file -> CompletableFuture.supplyAsync(() -> uploadWithFallback(file), imageUploadExecutor))
    .toList();
List<String> imageUrls = futures.stream()
    .map(CompletableFuture::join)   // 입력 순서 보장
    .toList();
```

주의사항:
- **순서 보장**: `futures` 리스트 순서대로 `join` → 결과 순서 = 입력 순서.
- **MultipartFile 수명**: 요청 스레드가 모든 future 를 `join` 할 때까지 동기 대기하므로, 워커 스레드에서 `file.getBytes()`/`getInputStream()` 호출 시점에 톰캣 임시파일/스트림 유효. 각 `MultipartFile` 은 독립 객체라 동시 접근 안전.
- **예외 전파**: 워커에서 던진 `CustomException(FILE_UPLOAD_ERROR)` 은 `join()` 시 `CompletionException` 으로 래핑됨 → 원인 예외(`getCause()`)를 풀어 다시 던지거나, `uploadWithFallback` 내부에서 처리되도록 유지. 기존 동작(한 장 실패 시 전체 실패) 보존.
- `@Transactional` 유지하되, 실제 외부 I/O(MinIO/FTP)는 트랜잭션 리소스가 아니므로 병렬 호출이 트랜잭션 경계와 충돌하지 않음 (DB 작업 없음).

## 영향 범위 / 비변경

- `/api/image/upload` 응답 스펙(반환 URL 리스트) 불변 — 순서·개수 동일.
- FE 압축본은 스킵되어 cwebp 0회, 원본/구버전은 기존대로 압축.
- `uploadWithFallback`, `uploadCompressed`, `uploadOriginal`, `deleteImages` 로직 불변.

## 테스트 관점

- 가드: webp+소용량 → null(스킵), webp+대용량 → 압축, jpeg → 압축.
- 설정: update 후 Redis 즉시 반영, 잘못된 pool-size/size 값 → `INVALID_REQUEST`.
- 병렬: N장 업로드 시 결과 URL 순서 == 입력 순서, 1장 실패 시 전체 실패 동작 유지.
- 멱등 마이그레이션: 재실행 시 중복 insert 안 됨.

## 작업 순서 (구현 단계)

1. Flyway 마이그레이션 (`image.*` seed)
2. `ImageCompressionService` 조건부 가드 + 로그
3. `imageUploadExecutor` Bean + `StorageService.saveImages` 병렬화
4. `AdminRequest`/`AdminResponse` 필드 + `SystemConfigService` get/update + `AdminApiController` 엔드포인트 + Docs `@ApiChangeLog`
