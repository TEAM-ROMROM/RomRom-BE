# ❗[버그][Storage] 이미지 업로드 시 application/octet-stream MIME 타입 거부 및 CompletionException 미처리

## 개요

FE 클라이언트가 압축된 이미지를 `application/octet-stream` Content-Type으로 전송할 경우 서버가 MIME 화이트리스트 검증에서 이를 거부하고, 이 예외가 `CompletableFuture` 비동기 처리 과정에서 `CompletionException`으로 래핑되어 `GlobalExceptionHandler`에 미처리 상태로 500 응답이 반환되는 두 가지 버그를 수정했습니다.

## 변경 사항

### MIME 타입 추론 로직 추가
- `RomRom-Domain-Storage/.../constant/MimeType.java`
  - `EXTENSION_TO_MIME_TYPE` 맵 추가: 파일 확장자(`webp`, `jpg`, `png` 등) → MIME 타입 매핑
  - `inferMimeTypeFromFilename(String filename)` 정적 메서드 추가: 파일명에서 확장자를 추출해 MIME 타입을 반환, 매핑 불가 시 `null` 반환

### MIME 검증 시 octet-stream 예외 처리
- `RomRom-Domain-Storage/.../util/FileUtil.java`
  - `validateFile()` 내 `application/octet-stream` 감지 시 `MimeType.inferMimeTypeFromFilename()`로 MIME 타입 추론
  - 추론 성공 시 해당 MIME 타입으로 이후 검증 진행, 추론 실패 시 기존 오류 흐름 유지

### CompletionException 핸들러 추가
- `RomRom-Common/.../exception/controller/GlobalExceptionHandler.java`
  - `@ExceptionHandler(CompletionException.class)` 핸들러 추가
  - 원인(`getCause()`)이 `CustomException`이면 기존 `handleCustomException()` 위임 → 올바른 HTTP 상태 코드(4xx) 반환
  - 원인이 `UgcProhibitedContentException`이면 `handleUgcProhibitedContentException()` 위임
  - 그 외 원인은 500 반환

## 주요 구현 내용

**버그 1 흐름 수정**

기존 `validateFile()`은 `file.getContentType()`이 `application/octet-stream`이면 화이트리스트(`image/jpeg` 등)에 없으므로 즉시 `INVALID_FILE_REQUEST`를 throw했습니다. 수정 후에는 `octet-stream` 감지 시 파일명(`file.getOriginalFilename()`)에서 확장자를 추출해 `EXTENSION_TO_MIME_TYPE`으로 실제 이미지 MIME 타입을 추론하고, 이후 동일한 화이트리스트 검증을 통과시킵니다.

```
application/octet-stream 수신
  → 파일명 확장자 추출 (예: photo.webp → "webp")
  → EXTENSION_TO_MIME_TYPE.get("webp") → "image/webp"
  → MimeType.isValidMimeType("image/webp") → 통과
```

**버그 2 흐름 수정**

`StorageService.saveImages()`는 `CompletableFuture.supplyAsync()`로 업로드를 병렬 처리하고 `.join()`으로 결과를 수집합니다. `join()`은 내부 예외를 `CompletionException`으로 래핑해 전파하는데, 기존 `GlobalExceptionHandler`에는 이 타입 핸들러가 없어 `@ExceptionHandler(Exception.class)`로 낙하해 500이 반환됐습니다. `CompletionException` 전용 핸들러를 추가해 `getCause()`로 원인 예외를 꺼낸 후 기존 핸들러로 위임함으로써 의도한 HTTP 상태 코드가 클라이언트에 전달됩니다.

## 주의사항

- 파일명 없이 `application/octet-stream`만 전송되는 경우(확장자 추론 불가)는 기존과 동일하게 `INVALID_FILE_REQUEST` 400 반환
- `CompletionException` 핸들러는 `SuspendedMemberException`, `EmailAlreadyRegisteredException` 등 현재 `CompletionException` 내부에 래핑될 가능성이 낮은 커스텀 예외는 별도 처리하지 않음 — 필요 시 핸들러 내 분기 추가 가능
