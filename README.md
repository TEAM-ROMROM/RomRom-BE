# RomRom-BE

<!-- 수정하지마세요 자동으로 동기화 됩니다 -->
## 최신 버전 : v0.0.21 (2025-08-19)

[전체 버전 기록 보기](CHANGELOG.md)

## 프로젝트 개요

RomRom-BE는 도메인 주도 설계(DDD) 기반의 모듈형 백엔드 아키텍처를 채택한 Java 엔터프라이즈 애플리케이션입니다. 멀티모듈 구조, 마이크로서비스 지향 설계, 헥사고날 아키텍처 패턴을 통한 고도의 확장성과 유지보수성 확보가 특징입니다.

## 기술 스택

### 핵심 프레임워크
- Spring Boot
- Spring Security
- Spring Data JPA/MongoDB
- Spring WebFlux

### 인프라 및 데이터베이스
- PostgreSQL (관계형 데이터)
- MongoDB (비정형 데이터)
- Redis (캐싱, 세션 관리)
- Apache Kafka (이벤트 스트리밍)
- Docker 컨테이너화
- Kubernetes 오케스트레이션

### 인증 및 보안
- JWT 토큰 기반 인증
- OAuth2.0 소셜 로그인
- SSL/TLS 암호화
- 해시 레지스트리

### AI 및 추천 엔진
- Google Vertex AI 연동
- 벡터 임베딩 기반 유사도 검색
- PG Vector 확장 활용

### 통합 및 스토리지
- FTP/SFTP 서비스 연동
- SMB 프로토콜 지원
- Firebase Cloud Messaging
- 분산 파일 시스템

### 개발 도구 및 품질
- Gradle 멀티 프로젝트 빌드
- Swagger/OpenAPI 문서화
- JUnit5 테스트 프레임워크
- CI/CD 자동화 파이프라인

## 아키텍처

RomRom-BE는 도메인별 모듈화를 통한 명확한 관심사 분리와 의존성 역전 원칙(DIP)을 기반으로 구성되었습니다.

- Application Layer: 비즈니스 유스케이스 오케스트레이션
- Domain Layer: 핵심 비즈니스 로직 및 규칙 캡슐화
- Infrastructure Layer: 외부 시스템 인터페이스 추상화
- Web Layer: REST API 엔드포인트 및 컨트롤러

## 주요 도메인

- 회원 관리 (Member)
- 인증/인가 (Auth)
- 상품 관리 (Item)
- 알림 시스템 (Notification)
- AI 서비스 (AI)
- 저장소 관리 (Storage)
- 신고 시스템 (Report)

## 핵심 기능

- 벡터 기반 유사 상품 추천
- 실시간 알림 시스템
- 위치 기반 서비스
- 멀티 스토리지 전략
- 토큰 기반 보안 인증
- 확장 가능한 태그 시스템
- 거래 요청 관리

## 모듈 구조

- `RomRom-Common`: 공통 의존성/유틸리티, 예외 처리, 파일(FTP/SMB) 서비스, 공용 엔티티 베이스, Swagger 설정 의존성
- `RomRom-Domain-Member`: 회원/위치/선호 카테고리 도메인 및 리포지토리/서비스
- `RomRom-Domain-Auth`: Spring Security + JWT 기반 인증, `TokenAuthenticationFilter`, `CustomUserDetailsService`
- `RomRom-Domain-Item`: 상품/이미지/거래요청/좋아요 도메인 및 서비스
- `RomRom-Domain-AI`: Vertex AI 클라이언트, 임베딩/유사도 계산 (`EmbeddingService`, Hibernate Vector)
- `RomRom-Domain-Notification`: FCM 토큰/알림 서비스
- `RomRom-Domain-Storage`: 스토리지 도메인 서비스 추상화
- `RomRom-Domain-Report`: 신고 도메인
- `RomRom-Application`: 애플리케이션 서비스 계층(유즈케이스 오케스트레이션)
- `RomRom-Web`: API 엔드포인트/컨트롤러 및 런타임 구성(`SecurityConfig`, `SwaggerConfig`, `RedisConfig`, `FtpConfig`, `SmbConfig`, `FirebaseConfig`, `VertexAiClientConfig`)

## 버전/릴리스

- 버전 관리: `version.yml` + Gradle 멀티모듈, 자동 체인지로그 생성
- 루트 Gradle: Spring Boot 3.4.x, Java 17 툴체인