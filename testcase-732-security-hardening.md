## 🧪 테스트 케이스: 보안 강화 — Swagger 접근 제한 / AUTH_WHITELIST 정리 / CORS Origin 화이트리스트
**이슈**: #732

### ✅ 1. Swagger 접근 제한

- [ ] 미인증 상태에서 `/docs/swagger-ui/index.html` 접근 시 `/admin/login?error=invalid`로 리다이렉트 되는지 확인
- [ ] 미인증 상태에서 `/v3/api-docs` 접근 시 리다이렉트 되는지 확인
- [ ] 관리자 로그인 후 쿠키(`accessToken`) 보유 상태에서 Swagger 정상 접근 되는지 확인
- [ ] 만료된 토큰으로 Swagger 접근 시 `/admin/login?error=expired`로 리다이렉트 되는지 확인
- [ ] `X-Requested-With: XMLHttpRequest` 헤더로 `/v3/api-docs` 접근 시 JSON 401 응답 반환 확인

### ✅ 2. AUTH_WHITELIST 정리

- [ ] 미인증 상태에서 `/api/test/sign-up` 요청 시 401 응답 반환 확인 (기존 허용 → 차단)
- [ ] 미인증 상태에서 `/api/test/send/notification/all` 요청 시 401 응답 반환 확인
- [ ] `/api/auth/login`, `/api/auth/reissue` 등 기존 화이트리스트 경로는 여전히 인증 없이 접근 가능한지 확인
- [ ] `/actuator/health` 미인증 접근 정상 동작 확인

### ✅ 3. CORS Origin 화이트리스트

- [ ] `https://romrom.xyz`에서 API 요청 시 CORS 허용 확인
- [ ] `https://test.romrom.xyz`에서 API 요청 시 CORS 허용 확인 (`*.romrom.xyz` 패턴)
- [ ] `http://localhost:3000`에서 API 요청 시 CORS 허용 확인
- [ ] 허용되지 않은 Origin(`https://evil.com`)에서 요청 시 CORS 오류 반환 확인

### ⚠️ 4. 엣지 케이스

- [ ] 유효하지 않은 토큰으로 `/admin/**` 접근 시 올바른 에러 처리 확인
- [ ] CORS preflight(`OPTIONS`) 요청 정상 처리 확인
- [ ] 관리자 로그인 페이지(`/admin/login`) 자체는 미인증 접근 가능한지 확인

### 📊 테스트 결과 요약

| 항목 | 결과 |
|------|------|
| 테스트 일자 | |
| 테스터 | |
| 환경 | prod / staging |
| 전체 결과 | ✅ 통과 / ❌ 실패 |
