---
title: "❗[버그][Storage] 이미지 업로드 시 application/octet-stream MIME 타입 거부 및 CompletionException 미처리"
labels: 작업전
---

🗒️ 설명
---

이미지 업로드(`POST /api/image/upload`) 시 두 가지 버그가 연속으로 발생합니다.

**버그 1 — application/octet-stream MIME 타입 거부**
- FE 클라이언트가 압축된 이미지를 `application/octet-stream` Content-Type으로 전송하면 서버가 거부함
- `FileUtil.validateFile()`이 `MimeType` 화이트리스트(`image/jpeg`, `image/webp` 등)만 허용하고 `application/octet-stream`은 포함하지 않아 즉시 `INVALID_FILE_REQUEST` 예외 발생

**버그 2 — CompletionException 미처리 (500 반환)**
- `StorageService.saveImages()`에서 `CompletableFuture.join()` 호출 시, 내부에서 발생한 `CustomException`이 `CompletionException`으로 래핑되어 전파됨
- `GlobalExceptionHandler`에 `CompletionException` 핸들러가 없어 `@ExceptionHandler(Exception.class)`로 낙하, 적절한 에러 코드 대신 500 Internal Server Error 반환

🔄 재현 방법
---

1. FE 클라이언트에서 WebP 등 압축된 이미지를 `application/octet-stream` Content-Type으로 `POST /api/image/upload` 요청
2. 서버 로그 확인:
   - `유효하지 않은 Mime 타입입니다. 요청된 MimeType: application/octet-stream`
   - `Exception Type: CompletionException`
   - `Exception Message: com.romrom.common.exception.CustomException: 잘못된 파일이 요청되었습니다.`
3. 클라이언트가 500 응답 수신 (정상이면 400 INVALID_FILE_REQUEST 또는 업로드 성공)

📸 참고 자료
---

```
ERROR --- [img-upload-1] c.r.s.u.FileUtil : 유효하지 않은 Mime 타입입니다. 요청된 MimeType: application/octet-stream
ERROR --- [img-upload-2] c.r.s.u.FileUtil : 유효하지 않은 Mime 타입입니다. 요청된 MimeType: application/octet-stream
ERROR --- [http-nio-8080-e] : Exception Type: CompletionException
ERROR --- [http-nio-8080-e] : Exception Message: com.romrom.common.exception.CustomException: 잘못된 파일이 요청되었습니다.
ERROR --- [nio-8080-exec-6] c.r.c.e.c.GlobalExceptionHandler : Unhandled Exception 발생: com.romrom.common.exception.CustomException: 잘못된 파일이 요청되었습니다.
```

관련 파일:
- `RomRom-Domain-Storage/src/main/java/com/romrom/storage/util/FileUtil.java`
- `RomRom-Domain-Storage/src/main/java/com/romrom/storage/constant/MimeType.java`
- `RomRom-Domain-Storage/src/main/java/com/romrom/storage/service/StorageService.java`
- `RomRom-Common/src/main/java/com/romrom/common/exception/controller/GlobalExceptionHandler.java`

✅ 예상 동작
---

- `application/octet-stream`으로 전송된 파일은 파일명 확장자(`.webp`, `.jpg` 등)로 MIME 타입을 추론하여 유효한 이미지 파일이면 업로드 성공
- 비동기 업로드 중 `CustomException` 발생 시 `CompletionException`을 언래핑하여 적절한 HTTP 상태 코드(400 등)로 응답

⚙️ 환경 정보
---

- **OS**: 서버 (Docker/Linux)
- **브라우저**: -
- **기기**: -

🙋‍♂️ 담당자
---
- **백엔드**: suh-lab
- **프론트엔드**: -
- **디자인**: -
