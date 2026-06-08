# 관리자 페이지 Swagger(API 문서) iframe 임베드 설계

> 작성일: 2026-06-08
> 목적: 관리자 사이드바에 "API 문서" 메뉴를 추가하고, 클릭 시 관리자 레이아웃(사이드바 유지) 안에 Swagger UI를 iframe으로 임베드한다.

## 배경 / 현재 상태 (코드로 검증 완료)

원격 서버(`api.romrom.xyz`, 시놀로지)가 샌드박스 네트워크에서 닿지 않아 실측은 불가했으나,
관련 코드를 직접 읽어 다음 사실을 확정했다.

### Swagger 경로 구성
- Swagger UI 경로: `/docs/swagger` (`RomRom-Web/src/main/resources/application.yml:54-55`)
- OpenAPI 스펙: `/v3/api-docs` (springdoc 기본)

### 인증/보안 (핵심)
- `AdminJwtAuthenticationFilter` (`RomRom-Domain-Auth/.../filter/AdminJwtAuthenticationFilter.java`)가
  **이미 Swagger 경로를 관리자 인증 대상으로 처리**한다.
  - `:42` — `isSwaggerPath = match("/docs/**") || match("/v3/api-docs/**")`
  - `:43` — 관리자 경로 또는 Swagger 경로면 이 필터가 인증을 수행
  - `:72-83` — **쿠키(`accessToken`)에서 토큰을 읽음** → iframe 페이지 요청 시 브라우저가 쿠키를 자동 전송하므로 토큰 전파 문제 없음
  - `:106-115` — `ROLE_ADMIN`이 아니면 차단 → **Swagger는 이미 관리자 전용으로 보호됨**
- 따라서 보안 설정(`SecurityUrls`, `SecurityConfig` 인증 규칙)은 **수정 불필요**.

### 미검증 항목 (구현/빌드 단계에서 확인)
- `SecurityConfig`에 `headers` 설정이 없어 Spring Security 6 기본 `X-Frame-Options: DENY`가 적용될 가능성.
  `DENY`면 같은 origin이라도 iframe이 빈 화면으로 막힌다. → **`frameOptions.sameOrigin()` 설정을 추가**해 대비한다.

## 관리자 페이지 구조 (파악 완료)

- 스택: Thymeleaf + Tailwind CSS 4 + DaisyUI 5 (`templates/admin/layout.html`)
- 사이드바: `layout.html`의 `<ul id="sidebarMenu">` 안에 `<li><a th:href>아이콘+라벨</a></li>` 패턴.
  active 표시는 `th:classappend="${currentMenu == 'xxx'} ? 'active'"`
- 라우팅: `AdminPageController` (`@RequestMapping("/admin")`)에서
  `@GetMapping("/xxx")` → `model.addAttribute("pageTitle", ...)` + `currentMenu` → `return "admin/xxx"`
- 각 페이지 템플릿은 `layout.html`을 상속하고 `layout:fragment="content"`를 채운다.

## 설계: 변경 사항 (총 4곳)

### 1. 사이드바 메뉴 항목 추가 — `templates/admin/layout.html`
`#sidebarMenu`의 "로그 관리"와 "설정" 사이에 `<li>` 1개 추가.

```html
<li>
    <a th:href="@{/admin/api-docs}" th:classappend="${currentMenu == 'api-docs'} ? 'active'">
        <i data-lucide="file-code" class="size-5"></i>
        API 문서
    </a>
</li>
```

### 2. 라우팅 핸들러 추가 — `AdminPageController`
```java
// API 문서(Swagger) 임베드 페이지
@GetMapping("/api-docs")
public String apiDocs(Model model) {
    model.addAttribute("pageTitle", "API 문서");
    model.addAttribute("currentMenu", "api-docs");
    return "admin/api-docs";
}
```

### 3. 새 템플릿 — `templates/admin/api-docs.html`
`layout.html`을 상속하고 `content` fragment 안에 Swagger UI iframe 1개.
높이는 navbar/footer/padding을 뺀 값으로 화면에 꽉 차게.

```html
<!doctype html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{admin/layout}">
<head>
    <title layout:fragment="title">API 문서</title>
</head>
<body>
<div layout:fragment="content">
    <!-- Swagger UI를 같은 origin iframe으로 임베드 (관리자 인증은 쿠키 토큰으로 자동 통과) -->
    <iframe th:src="@{/docs/swagger}"
            title="Swagger API 문서"
            class="w-full rounded-box border border-base-300 bg-base-100"
            style="height: calc(100vh - 9rem);"></iframe>
</div>
</body>
</html>
```

### 4. iframe 허용 헤더 설정 — `SecurityConfig.filterChain`
같은 origin iframe 임베드를 허용한다.

```java
.headers(headers -> headers
    // 같은 origin iframe 임베드 허용 (관리자 페이지 내 Swagger UI 임베드용)
    .frameOptions(frameOptions -> frameOptions.sameOrigin()))
```

## 검증 계획 (빌드/배포 후)
1. 관리자 로그인 → 사이드바 "API 문서" 클릭 → 레이아웃 안에 Swagger UI가 정상 렌더되는지.
2. iframe이 빈 화면이면 응답 헤더의 `X-Frame-Options` 확인 → `SAMEORIGIN`인지 점검.
3. 비관리자(또는 비로그인) 상태로 `/admin/api-docs` 직접 접근 시 로그인 리다이렉트되는지.

## 범위에서 제외 (YAGNI)
- Swagger 내부 "Authorize / Try it out" 실제 API 호출 동작 보장 — 이번 목표는 "문서 열람 임베드".
- Swagger 접근 권한 구조 변경 — 이미 관리자 전용이므로 손대지 않는다.
