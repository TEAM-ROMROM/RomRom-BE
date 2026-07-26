# 서브프로젝트 A — 설정/인프라 위생 및 측정 기반 확보

- 작성일: 2026-07-27
- 상태: 설계 승인 대기
- 선행: 없음
- 후속: B(관리자 페이지), C(관측성/로깅), D(백엔드 성능/구조)

## 배경

RomRom-BE는 12개 모듈 / Java 346파일 규모로 성장했으나, 애플리케이션 설정은 초기 상태에서
크게 손대지 않은 채 유지돼 왔다. 코드 리뷰 과정에서 **설정 키가 잘못된 위치에 있어 무시되거나,
존재하지 않는 속성이 동작하는 것처럼 문서화돼 있거나, 운영에 부적합한 값이 그대로 적용되는**
사례가 다수 확인됐다.

이 문서는 코드 변경을 최소화하면서 설정만으로 얻을 수 있는 성능·안정성 개선과,
이후 B/C/D 단계의 개선 효과를 **숫자로 증명하기 위한 측정 기반**을 확보하는 것을 다룬다.

### 전체 로드맵에서의 위치

| 단계 | 내용 | 리스크 | 선행 |
|---|---|---|---|
| **A (본 문서)** | 설정/인프라 위생 + 측정 기반 | 낮음 | 없음 |
| B | 관리자 페이지 (구조 + UX + 기능 + 버그) | 중 | A |
| C | 관측성/로깅 (Async Appender, MDC traceId) | 낮~중 | A |
| D | 백엔드 성능/구조 (N+1, OSIV 전환, 거대 서비스 분해) | 높음 | A, C |

D를 마지막에 두는 이유: N+1 제거를 증명하려면 A의 커넥션 풀/쿼리 메트릭과 C의 요청 추적이
먼저 있어야 한다. 측정 수단 없이 진행하면 개선 여부가 추측에 머문다.

## 실측 근거

모든 항목은 코드/설정 실측으로 확인했다.

| # | 문제 | 위치 | 확인 방법 |
|---|---|---|---|
| 1 | `open-in-view` 키가 잘못된 경로에 있어 무시됨 | `application.yml` `spring.jpa.properties.hibernate.open-in-view` | 올바른 키는 `spring.jpa.open-in-view`. 현재 설정은 Hibernate에 전달되지만 Spring Boot의 OSIV 인터셉터와 무관 → 기본값 `true`로 동작 중 |
| 2 | 운영에서 SQL 전량 stdout 출력 | `application.yml` `show_sql`, `format_sql`, `use_sql_comments` 모두 `true` | prod 프로필에 오버라이드 없음. `show_sql`은 logger가 아닌 stdout 직행이라 로그레벨로 차단 불가 |
| 3 | 커넥션 풀 설정 전무 | 어느 yml에도 `hikari` 없음 | `grep -rn "hikari" RomRom-Web/src/main/resources/*.yml` → 0건. HikariCP 기본 풀 크기 10 |
| 4 | JPA 배치/페치 튜닝 전무 | 어느 yml에도 `batch_size`, `default_batch_fetch_size` 없음 | 동일 grep → 0건 |
| 5 | 운영 로그레벨 DEBUG | `application-prod.yml` `com.romrom: DEBUG`, suh-logger/suh-api-log DEBUG | `logback-spring.xml`에도 `com.romrom` DEBUG가 중복 하드코딩 |
| 6 | 메트릭 노출 없음 | `application.yml` `management.endpoints.web.exposure.include: health` | actuator 의존성은 이미 존재(`RomRom-Web/build.gradle:23`)하나 health만 노출 |
| 7 | 존재하지 않는 Flyway 속성 | `application.yml` `spring.flyway.repair-on-migrate: true` | Spring Boot 3.4.1 `spring-configuration-metadata.json`에 해당 키 없음. 주석은 "마이그레이션 전 자동 repair"라고 설명하나 실제로는 무시됨 |

### OSIV 관련 추가 실측 (A 범위 제외 근거)

| 항목 | 값 |
|---|---|
| `FetchType.LAZY` 연관관계 총 개수 | 28 |
| `@JsonIgnore`로 보호된 LAZY 필드 | 3 (`MemberItemCategory.member`, `MemberLocation.member`, `ItemImage.item`) |
| `jackson-datatype-hibernate` 모듈 등록 | 없음 |
| 미보호 LAZY 필드 예시 | `ChatRoom.tradeSender/tradeReceiver`, `TradeRequestHistory.takeItem/giveItem`, `TradeReview.*`, `ItemReport.*`, `MemberReport.*`, `HiddenItem.*`, `ViewHistory.*`, `NotificationHistory.*`, `FcmToken.*` |
| `Item.member` | LAZY이나 `ItemRepositoryImpl` 76/98/158행에서 fetch join으로 방어됨 |

## 설계

### A-1. JPA/Hibernate 설정 교정

`RomRom-Web/src/main/resources/application.yml`

```yaml
spring:
  jpa:
    # 현재 실제 동작값을 명시 (기존에는 키 위치 오류로 무시되고 있었음).
    # false 전환은 LAZY 연관관계 전수 감사가 선행돼야 하므로 D에서 처리한다.
    open-in-view: true
    generate-ddl: true          # 변경하지 않음
    hibernate:
      ddl-auto: update          # 변경하지 않음
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_batch_fetch_size: 100
        jdbc:
          batch_size: 50
          fetch_size: 100
        order_inserts: true
        order_updates: true
        query:
          in_clause_parameter_padding: true
        # show_sql / format_sql / use_sql_comments 삭제 → 아래 로깅으로 대체
```

SQL 로깅은 logback을 경유하도록 교체한다. `application-dev.yml`에만 추가:

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE
```

prod/test 프로필에는 추가하지 않는다(= 출력 없음).

**`default_batch_fetch_size: 100`이 A의 최대 성능 이득이다.** 코드를 변경하지 않고 기존
지연 로딩 N+1의 상당수를 배치 `IN` 조회로 접는다. D 착수 전에도 즉시 효과가 발생한다.

`in_clause_parameter_padding: true`는 `IN` 절 파라미터 개수를 2의 거듭제곱으로 패딩해
PostgreSQL 쿼리 플랜 캐시 히트율을 높인다. `default_batch_fetch_size`와 세트로 동작한다.

#### 스키마 관련 설정을 변경하지 않는 이유

`ddl-auto: update` + `generate-ddl: true`는 **의도된 설계**다. Spring이 자동 처리할 수 있는
스키마 변경은 Hibernate에 맡기고, 그 밖의 변경(제약조건, 데이터 마이그레이션, 인덱스)만
Flyway로 처리하는 운영 방식이다. 본 작업에서 건드리지 않는다.

### A-2. 커넥션 풀

프로필별로 값이 동일하므로 공통 파일인 `application.yml`에 둔다
(각 프로필 yml에는 접속 정보만 유지 → 중복 제거).

```yaml
spring:
  datasource:
    hikari:
      pool-name: RomRomHikariPool
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 5000
```

풀 크기 산정 근거: 블루/그린 배포로 두 인스턴스가 동시 기동되는 구간이 존재하므로
실제 최대 커넥션은 `20 × 2 = 40`이다. PostgreSQL 기본 `max_connections`(100) 내에서
다른 클라이언트 여유를 남긴다.

`leak-detection-threshold: 5000`은 5초 이상 반환되지 않는 커넥션을 경고 로그로 남긴다.
OSIV가 켜져 있는 현 상태에서 커넥션 보유 시간이 실제로 얼마나 긴지 계량하는 수단이며,
D의 OSIV 전환 작업에 필요한 근거 데이터를 확보한다.

### A-3. 로그레벨 정상화

- `application-prod.yml`, `application-test.yml`: `com.romrom` → `INFO`,
  `me.suhsaechan.suh-logger` / `suh-api-log` → `INFO`
- `application-dev.yml`: DEBUG 유지
- `logback-spring.xml`: 하드코딩된 `<logger name="com.romrom" level="DEBUG"/>` 제거.
  프레임워크 로거(`org.springframework`, `org.hibernate` 등) 레벨도 yml과 중복이므로 제거하고
  **로그레벨의 단일 소스를 yml로 통일**한다. Appender 정의는 logback에 유지한다.

### A-4. 측정 기반 (actuator + Prometheus)

`RomRom-Web/build.gradle`

```gradle
implementation 'io.micrometer:micrometer-registry-prometheus'
```

`application.yml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    tags:
      application: romrom
```

보안 조치는 불필요하다. `SecurityUrls.AUTH_WHITELIST`에는 `/actuator/health`만 등록돼 있어
`/actuator/prometheus`와 `/actuator/metrics`는 기존 JWT 필터 체인에 의해 자동 보호된다.
외부 스크래핑이 필요해지는 시점에 `SECURED_API_URLS`(HMAC 서명 검증 경로)로 옮긴다.

확보되는 지표:

- `hikaricp_connections_active` / `_pending` / `_usage` — 풀 포화 및 커넥션 보유 시간
- `hibernate_query_executions` / `_time` — 쿼리 수·시간 (A-1 배치 튜닝 효과 측정)
- `http_server_requests_seconds` — 엔드포인트별 p95/p99
- `jvm_gc_*`, `jvm_memory_*`

### A-5. Flyway 죽은 설정 정리

동작은 변경하지 않는다.

```yaml
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    sql-migration-suffixes: .sql
    # 과거 마이그레이션 파일이 적용 후 수정된 이력이 있어 체크섬 검증을 끈 상태다.
    # 켜면 배포가 그 지점에서 중단되므로, 체크섬 정합화 작업 이후에 활성화한다.
    validate-on-migrate: false
    out-of-order: false
    # repair-on-migrate 제거 — Spring Boot 3.4.1에 존재하지 않는 속성이며 무시되고 있었다.
```

`validate-on-migrate: false`를 유지하는 이유: 실제로 동작 중인 유일한 안전장치가 이것이다.
`repair-on-migrate`가 무시돼 왔으므로 자동 repair는 한 번도 수행된 적이 없고, 검증을 켜면
체크섬이 깨진 마이그레이션에서 배포가 즉시 실패한다. 체크섬 정합화는 별도 이슈로 분리한다.

## 검증 전략

설정 변경은 단위 테스트로 포착되지 않으므로 기동 검증과 배포 후 실측을 병행한다.

### 자동 테스트

1. **OSIV 설정 회귀 방지 테스트** — `spring.jpa.open-in-view` 프로퍼티가 의도한 값으로
   바인딩되는지 단언한다. 잘못된 키 위치로 인해 설정이 무시돼 온 이력이 있으므로,
   D에서 `false`로 전환할 때 이 테스트가 전환 완료를 증명하는 장치가 된다.
2. **HikariCP 설정 바인딩 테스트** — `HikariDataSource`의 `maximumPoolSize`,
   `leakDetectionThreshold`가 yml 값대로 주입되는지 단언한다.
3. **프로필별 컨텍스트 기동 테스트** — dev/test 프로필로 `@SpringBootTest` 기동 성공 확인.
4. **actuator 노출 테스트** — `/actuator/prometheus`가 인증 없이는 접근 불가하고,
   관리자 인증 시 200을 반환하는지 확인.

### 배포 후 실측 (`/pro-ssh`)

1. 기동 로그에 `show_sql` 유래 SQL 출력이 사라졌는지 확인
2. `HikariPool-1` → `RomRomHikariPool`로 풀 이름이 바뀌었는지 확인 (설정 반영 증거)
3. `Connection leak detection` 경고 발생 여부 수집 — D의 OSIV 작업 근거 데이터
4. `/actuator/prometheus` 응답에서 `hikaricp_connections_active`, `hibernate_query_*` 수집
5. **주요 API p95를 기록해 베이스라인으로 남긴다** — B/C/D 비교 기준

### 베이스라인 기록 대상 엔드포인트

- `POST /api/item/list` (메인 피드, 최다 호출)
- `POST /api/admin/dashboard/stats`
- `POST /api/admin/members/list`
- 채팅방 목록 조회

## 범위 밖 (명시적 제외)

| 항목 | 이유 | 이관 |
|---|---|---|
| `ddl-auto`, `generate-ddl` 변경 | 의도된 운영 설계 | 변경 안 함 |
| Flyway `validate-on-migrate` 활성화 | 체크섬 정합화 선행 필요 | 별도 이슈 |
| `open-in-view: false` 전환 | 미보호 LAZY 연관관계 25개 전수 감사 필요 | **D** |
| 비동기 Appender, MDC traceId, 구조화 로깅 | 별도 서브프로젝트 | **C** |
| N+1 코드 수정, `findAll()` 3곳 페이징화 | 코드 변경 범위 | **D** |
| `AdminApiController` 1752행 분해 | 관리자 개편 범위 | **B** |
| 거대 서비스 분해 (`ItemService` 913행 등) | 코드 변경 범위 | **D** |
| Spring Boot 3.4.1 버전업 | 다른 변경과 섞이면 원인 추적 불가 | 별도 이슈 |
| multipart 200MB/1000MB 기본값 조정 | 실사용 패턴 확인 선행 필요 | 별도 이슈 |

## 리스크

| 리스크 | 영향 | 완화 |
|---|---|---|
| `default_batch_fetch_size: 100`이 일부 쿼리에서 과대 `IN` 절 생성 | 특정 쿼리 지연 | A-4 메트릭으로 배포 후 쿼리 시간 비교, 문제 시 값 하향 |
| 로그레벨 INFO 전환으로 장애 조사 정보 감소 | 디버깅 난이도 상승 | C에서 MDC traceId 도입으로 보완. 필요 시 SystemConfig 기반 런타임 조정 검토 |
| Hikari `connection-timeout: 3000`이 부하 시 짧을 수 있음 | 요청 실패 | 풀 포화 메트릭으로 관찰 후 조정 |
| actuator 노출 확대 | 정보 노출 | JWT 필터 체인으로 자동 보호됨을 테스트로 검증 |

## 성공 기준

1. 운영 로그에서 SQL 전량 출력이 사라진다
2. `/actuator/prometheus`에서 풀·쿼리·HTTP 지표가 수집된다
3. 주요 4개 엔드포인트의 p95 베이스라인이 기록된다
4. 설정 회귀 방지 테스트 4종이 통과한다
5. 기존 API 동작에 회귀가 없다 (배포 후 로그에 신규 예외 없음)
