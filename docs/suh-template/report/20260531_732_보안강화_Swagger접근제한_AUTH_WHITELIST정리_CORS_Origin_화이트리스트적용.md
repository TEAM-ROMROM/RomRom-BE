# 보안 강화: Swagger 접근 제한 / AUTH_WHITELIST 정리 / CORS Origin 화이트리스트 적용

## 개요

프로덕션 환경에서 Swagger UI와 API 문서가 인증 없이 접근 가능하던 보안 취약점을 수정하고, 불필요한 테스트 API 경로를 제거하며, CORS에서 모든 Origin을 허용하던 설정을 명시적 도메인 목록으로 제한하였다.

## 변경 사항

### 인증 화이트리스트 정리
- `SecurityUrls.java`: `AUTH_WHITELIST`에서 `/docs/**`, `/v3/api-docs/**`, `/api/test/**` 제거

### Swagger 경로 인증 적용
- `AdminJwtAuthenticationFilter.java`: 관리자 경로 외 Swagger 경로(`/docs/**`, `/v3/api-docs/**`)도 인증 검사 대상에 포함. 미인증 접근 시 `/admin/login`으로 리다이렉트

### CORS 설정 강화
- `SecurityConfig.java`: `setAllowedOriginPatterns("*")` → 명시적 도메인 목록으로 변경
  - `https://romrom.xyz`
  - `https://*.romrom.xyz`
  - `http://localhost:8080`
  - `http://localhost:3000`

## 주요 구현 내용

`AdminJwtAuthenticationFilter`에서 Swagger 경로 감지 로직을 추가해, 관리자 JWT 쿠키(`accessToken`) 없이 `/docs/**` 또는 `/v3/api-docs/**` 접근 시 `/admin/login`으로 리다이렉트하거나 JSON 401 응답을 반환한다. `AUTH_WHITELIST`에서 Swagger 경로를 제거했으므로 Spring Security 레벨에서도 인증이 필요하다.

## 주의사항

- `/api/test/**` 경로 제거로 인해 테스트 빌드에서 해당 API 사용 시 401 응답 발생. 필요 시 `SECURED_API_URLS` 또는 별도 처리 필요
- Swagger 접근이 필요한 경우 반드시 관리자 로그인 후 쿠키 포함 요청 필요
