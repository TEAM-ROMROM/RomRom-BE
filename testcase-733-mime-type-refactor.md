## 🧪 테스트 케이스: 이미지 MIME 타입 검증 리팩토링
**이슈**: #733

### ✅ 1. 정상 업로드 동작 확인

- [ ] `image/jpeg` (jpg) 파일 업로드 성공 확인
- [ ] `image/png` (png) 파일 업로드 성공 확인
- [ ] `image/webp` (webp) 파일 업로드 성공 확인
- [ ] `application/octet-stream` Content-Type + `.jpg` 확장자 파일 업로드 성공 확인 (iOS 대응)
- [ ] `application/octet-stream` Content-Type + `.png` 확장자 파일 업로드 성공 확인
- [ ] `application/octet-stream` Content-Type + `.webp` 확장자 파일 업로드 성공 확인 (Android 대응)

### ⚠️ 2. 엣지 케이스 / 차단 케이스

- [ ] `application/octet-stream` + 확장자 없는 파일명 → 400 에러 반환 확인
- [ ] `application/octet-stream` + `.exe` 확장자 파일 → 400 에러 반환 확인
- [ ] `application/pdf` MIME 타입 파일 → 400 에러 반환 확인
- [ ] 빈 파일(0 bytes) → 400 에러 반환 확인
- [ ] MIME 타입 없음(null/empty) → 400 에러 반환 확인

### ✅ 3. 다중 파일 업로드

- [ ] jpg + png 혼합 2장 동시 업로드 성공 확인
- [ ] `octet-stream` 파일 2장 동시 업로드 성공 확인 (병렬 처리)
- [ ] 정상 파일 + 비정상 파일 혼합 업로드 시 전체 실패 처리 확인

### 📊 테스트 결과 요약

| 항목 | 결과 |
|------|------|
| 테스트 일자 | |
| 테스터 | |
| 환경 | prod / staging |
| 전체 결과 | ✅ 통과 / ❌ 실패 |
