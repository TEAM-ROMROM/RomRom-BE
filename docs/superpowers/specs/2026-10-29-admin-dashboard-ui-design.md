# 관리자 운영 대시보드 · 화면 개편 설계 (#827)

## 의도
관리자가 대시보드 하나로 "서비스가 잘 돌아가는가"를 판단하고, 처리할 일을 바로 찾게 한다.
지금은 숫자 4개와 최근 목록뿐이라 성장·활동·거래 전환·안전 상태를 알 수 없다. 화면 전체도 기본 테마 그대로라 위계가 없다.
사용자 위임: "필요한 거 판단해서 전부" → 아래 결정은 모두 구현자 판단(사용자 확인 전 결정)임.

## 목표
- 기간(7·30·90일) 기준 핵심 지표 + 전기간 대비 증감 + 추세 차트 + 구성 분포 + 안전·품질 지표
- 처리 필요한 일(대기 신고, 완료요청 거래)을 대시보드와 사이드바에서 바로 확인
- 관리자 전 화면 공통 셸(테마·사이드바·머리글·카드·표) 개편, 라이트·다크·모바일 폭 지원

## 하지 않는 것
- 각 목록 화면의 기능·필터 로직 변경 (공통 스타일만 적용)
- 실시간 갱신(웹소켓) 대시보드, 지표 저장용 새 테이블·배치
- Admin API 규칙 위반(단일 AdminRequest/AdminResponse, POST multipart 유지)

## 실패로 보는 것
- 대시보드가 느려져 운영 DB에 부담 (요청 1회 = 집계 쿼리 묶음, 인덱스 없는 대용량 스캔 금지)
- 차트 숫자가 목록 화면 숫자와 어긋남 (같은 기준 사용)
- 다크 모드에서 차트 글자·격자가 안 보임

## 지표 정의 (기간 = 오늘 포함 N일, KST 날짜 기준)
| 지표 | 정의 |
|---|---|
| 신규 회원 | `member.created_date` 기간 내, 관리자 계정 제외 |
| 전체 회원 | 활성 회원 수(기존 `countActiveMembers`) |
| DAU·WAU·MAU | `member.last_active_at`이 지금 기준 1·7·30일 이내 (탈퇴·관리자 제외) |
| 지금 접속 | 기존 online-stats (앱 5분 heartbeat, 채팅 온라인) |
| 신규 물품 | `item.created_date` 기간 내 |
| 활성 물품 | 기존 `countByIsDeletedFalse` |
| 거래 요청 | `trade_request_history.created_date` 기간 내 |
| 거래 완료 | 상태 TRADED 이면서 `updated_date` 기간 내 (완료 시각 컬럼이 없어 근사, 화면에 표기) |
| 완료 전환율 | 거래 완료 ÷ 거래 요청 (근사) |
| 대기 신고 | 물품·회원 신고 PENDING 전체 |
| 신규 후기 | `trade_review.created_date` 기간 내 |
| 로그인 회원(일별) | 로그인 이력 SUCCESS, 날짜별 고유 회원 |
| 채팅 메시지·물품 조회·AI 호출 | 각 이력의 날짜별 건수 (AI는 실패 건 별도) |

전기간 대비: 같은 길이의 직전 기간 값과 비교, 직전 값 0이면 증감률 없음(null).

## API
`POST /api/admin/dashboard/analytics` (multipart, `AdminRequest.analyticsPeriodDays`: 7·30·90, 기본 30, 1~180 외 400)
응답 `AdminResponse.dashboardAnalytics`:
- `periodDays`, `startDate`, `endDate`, `dates[]`
- `dailySeries{key: long[]}`: newMembers, newItems, tradeRequests, completedTrades, loginMembers, chatMessages, itemViews, aiCalls, aiFailures (빈 날짜 0 채움)
- `metrics{key: {currentValue, previousValue, changeRate}}`: 기간 비교 지표
- 단일 값: totalMembers, dailyActiveMembers, weeklyActiveMembers, monthlyActiveMembers, activeItems, pendingReports, completeRequestedTrades
- 분포 `[{key, label, count}]`: tradeStatus, itemCategory(활성 물품), region(시·도 Top 10), socialPlatform, deviceType, reportStatus(기간), reviewRating(기간), aiUsageType(기간)
- `loginHourlyCounts[24]` (기간 로그인 성공, KST 시)
- 라벨은 서버 enum 설명이 있으면 그 값, 없으면 화면용 한글 매핑 한 곳(서비스 상수)

## 구현 구조
- Application `AdminDashboardAnalyticsService`: 기간 계산·조립 (300줄 이하, 넘으면 쿼리 묶음을 협력 클래스로)
- 날짜별 집계: 각 도메인 Repository에 native 일별 count 쿼리 (`CAST(created_date AS date)`), 공통 투영 `DailyCountProjection`(Common)
- Mongo 집계(로그인·채팅·AI): Application에서 `MongoTemplate` aggregation, 날짜·시간은 `Asia/Seoul` 기준 `$dateToString`/`$hour`
- 0 채우기·증감률 계산은 순수 함수로 분리해 단위 테스트
- `AdminDashboardControllerDocs` + `@ApiChangeLog`

## 화면
- 공통 셸(`layout.html`, `common.css`, `common.js`)
  - CDN 버전 고정: `@tailwindcss/browser@4.3.3`, `daisyui@5.7.47`, `lucide@1.54.0`, `chart.js@4.5.1`
  - 테마 토큰: RomRom 노랑 `#FFC300`(primary), 검정 `#1D1E27`(neutral), 라이트·다크 각각 정의
  - 사이드바: 묶음 5개(개요 / 회원·콘텐츠 / 안전 / 운영 / 시스템), 전체 높이 고정, 신고 대기·완료요청 배지(모든 관리자 페이지 공통 모델 값)
  - 머리글: 제목 + 한 줄 설명, 카드·표·배지 스타일 통일(호버·여백·숫자 정렬)
- 대시보드(`dashboard.html` + `static/js/admin-dashboard.js` + `static/js/admin-charts.js`)
  1. 머리글: 기간 7·30·90일, 마지막 갱신 시각, 새로고침
  2. 처리 필요 띠: 대기 신고·완료요청 거래 (0이면 "처리할 일 없음")
  3. 핵심 지표 카드 8개: 값 + 전기간 대비 ▲▼ + 미니 추세선
  4. 성장 추이(선, 4계열) | 거래 상태 분포(도넛)
  5. 활동: 일별 로그인 회원·채팅·조회(막대+선) | 시간대별 접속(막대 24)
  6. 구성: 카테고리(가로 막대) | 지역 Top 10(가로 막대) | 가입 경로·기기(도넛 2)
  7. 안전·품질: 신고 상태(도넛) | 후기 평점(막대) | AI 호출·실패율(막대+선)
  8. 최근 가입·물품·교환완료 목록 (기존 API 재사용)
- 차트 색·글자는 CSS 변수에서 읽고 테마 전환 시 다시 그림

## 검증
- 단위: 기간 계산, 0 채우기, 증감률, 라벨 매핑
- 로컬 dev 실기동: 분석 API 응답, 대시보드·전 메뉴 화면 캡처(라이트·다크·390px)
- 운영 배포 후 대시보드 응답 시간·화면 확인

## 구현자에게 맡긴 것
- 차트 색 순서, 카드 아이콘, 정확한 여백 값
- 쿼리 묶음 분리 방식(서비스 300줄 기준)
