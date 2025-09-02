# 🎯 RomRom-BE 코드 가이드라인

> Spring Boot 멀티모듈 중고거래 앱 백엔드 프로젝트

---

## 📋 프로젝트 구조

### 멀티모듈 아키텍처
```
RomRom-BE/
├── RomRom-Common/           # 공통 모듈 (예외, 상수, 유틸)
├── RomRom-Domain-Auth/      # 인증 도메인
├── RomRom-Domain-Member/    # 회원 도메인  
├── RomRom-Domain-Item/      # 아이템 도메인
├── RomRom-Domain-AI/        # AI 도메인
├── RomRom-Application/      # 애플리케이션 서비스
└── RomRom-Web/              # 웹 계층 (Controller, Config)
```

### 패키지별 역할
- **Common**: 전역 상수, 예외 처리, 유틸리티 클래스
- **Domain**: 각 도메인별 비즈니스 로직 (Entity, Repository, Service)
- **Application**: 도메인 간 조합 로직
- **Web**: REST Controller, 설정 클래스

---

## 🏗️ 핵심 개발 규칙

### 1. 명명 규칙
```java
// ✅ Boolean 필드는 반드시 is 접두사
private boolean isActive;
private boolean isFirstLogin;
private boolean isDeleted;

// ✅ Entity는 UUID + 복합키 형태
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID memberId;  // Entity명 + Id

// ✅ 컬렉션은 복수형만 (List 단어 금지)
private List<Member> members;  // ✅
private List<Member> memberList; // ❌
```

### 2. API 패턴 (모바일 특성)
```java
// ✅ 모든 API는 POST + MULTIPART_FORM_DATA
@PostMapping(value = "/api/auth/sign-in", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@LogMonitor  // 로깅 어노테이션 필수
public ResponseEntity<AuthResponse> signIn(@ModelAttribute AuthRequest request) {
    return ResponseEntity.ok(authService.signIn(request));
}

// ❌ PathVariable 사용 금지 (모바일 앱 특성상)
@GetMapping("/api/members/{id}")  // 사용 안 함
```

### 3. Controller 작성법
```java
@RestController
@RequiredArgsConstructor
@Tag(name = "인증 API", description = "소셜 로그인 API")
@RequestMapping("/api/auth")
public class AuthController implements AuthControllerDocs {
    
    private final AuthService authService;
    
    @Override
    @PostMapping(value = "/sign-in", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AuthResponse> signIn(@ModelAttribute AuthRequest request) {
        // Controller는 Service 호출만
        return ResponseEntity.ok(authService.signIn(request));
    }
}
```

### 4. Service 패턴
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    
    @Transactional  // 쓰기 작업시에만 추가
    public AuthResponse signIn(AuthRequest request) {
        // nvl 유틸로 null 체크
        String email = nvl(request.getEmail(), "");
        if (email.isEmpty()) {
            throw new CustomException(ErrorCode.EMAIL_REQUIRED);
        }
        
        // Builder 패턴으로 객체 생성
        Member member = Member.builder()
            .email(email)
            .isActive(true)
            .build();
            
        return AuthResponse.of(member, generateToken(member));
    }
}
```

### 5. DTO 패턴
```java
@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class AuthRequest {
    
    @Schema(description = "로그인 플랫폼", defaultValue = "KAKAO")
    private SocialPlatform socialPlatform;
    
    private String email;
    private boolean isMarketingInfoAgreed;  // Boolean은 is 접두사
}

@ToString
@AllArgsConstructor
@Getter
@Builder
@NoArgsConstructor
public class AuthResponse {
    private String accessToken;
    private boolean isFirstLogin;
    
    // 정적 팩토리 메서드
    public static AuthResponse of(Member member, String token) {
        return AuthResponse.builder()
            .accessToken(token)
            .isFirstLogin(member.getIsFirstLogin())
            .build();
    }
}
```

### 6. Entity 패턴
```java
@Entity
@Getter
@Setter
@SuperBuilder  // 상속 시에는 @SuperBuilder 사용
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)  // 상속 시 callSuper = true
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Member extends BasePostgresEntity {  // 필요시에만 상속
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)  // nullable 순서
    private UUID memberId;  // Entity명 + Id
    
    @Column(unique = true)  // JPA 기본 생성 규칙 사용 (name 명시 안함)
    private String email;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;  // Boolean은 is 접두사
    
    @Enumerated(EnumType.STRING)
    private SocialPlatform socialPlatform;
    
    // @ManyToOne만 사용 (양방향 연관관계 금지)
    @ManyToOne(fetch = FetchType.LAZY)
    private MemberLocation memberLocation;  // JoinColumn 명시 안함
}
```

### 7. 공통 유틸리티
```java
// null 체크
String email = CommonUtil.nvl(request.getEmail(), "default@email.com");

// 빈 문자열 체크
if (CommonUtil.nvl(request.getEmail()).isEmpty()) {
    throw new CustomException(ErrorCode.EMAIL_REQUIRED);
}

// Builder 패턴 사용
Member member = Member.builder()
    .email(request.getEmail())
    .isActive(true)
    .build();
```

---

## ⚠️ 금지 사항

### 절대 하면 안 되는 것들
```java
// ❌ Boolean 변수에 is 접두사 없음
private boolean active;
private boolean deleted;

// ❌ PathVariable 사용
@GetMapping("/api/members/{id}")

// ❌ 양방향 연관관계
@OneToMany(mappedBy = "parent")
private List<Child> children;

// ❌ 컬렉션에 List 단어 사용 > 복수형 s 사용
private List<Member> memberList;

// ❌ null 체크 없이 사용
String email = request.getEmail();  // nvl() 사용해야 함

// ❌ Controller에서 비즈니스 로직
@PostMapping("/api/auth/sign-in")
public ResponseEntity<AuthResponse> signIn(@ModelAttribute AuthRequest request) {
    if (request.getEmail() == null) {  // Service에서 해야 함
        throw new CustomException(ErrorCode.EMAIL_REQUIRED);
    }
}

// ❌ @Column name 명시적 지정
@Column(name = "member_name")
private String memberName;

// ❌ @JoinColumn 명시적 지정
@JoinColumn(name = "member_id")
private Member member;
```

---

## 🚀 개발 체크리스트

### Controller
- [ ] `AuthControllerDocs` 인터페이스 구현
- [ ] `@LogMonitor` 어노테이션 추가
- [ ] `MediaType.MULTIPART_FORM_DATA_VALUE` 사용
- [ ] Controller에 비즈니스 로직 없음

### Service
- [ ] `@Transactional(readOnly = true)` 클래스 레벨 설정
- [ ] `CommonUtil.nvl()` 사용한 null 체크
- [ ] Builder 패턴 사용
- [ ] CustomException으로 예외 처리

### DTO
- [ ] Boolean 필드에 `is` 접두사 사용

### Entity
- [ ] UUID 타입 ID 사용 (`Entity명 + Id` 패턴)
- [ ] `@SuperBuilder` 사용 (상속 시)
- [ ] `@ToString(callSuper = true)` 사용 (상속 시)
- [ ] `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` 추가
- [ ] `@Column` 어노테이션에서 `name` 명시적 지정 금지 (JPA 기본 규칙 사용)
- [ ] `@JoinColumn` 명시적 지정 금지 (JPA 기본 규칙 사용)
- [ ] `@ManyToOne`만 사용 (양방향 연관관계 금지)
- [ ] Boolean 필드에 `is` 접두사 사용
- [ ] `@Builder.Default` 사용하여 기본값 설정

---

## 📝 개발 명령어

** 매우 중요한 CLI 명령어 사용법**:
```bash 
source ~/.zshrc &&
```
를 붙여서 모든 명령어를 실행해야지 작동함

**코드 변경 후 마지막에 꼭 실행**:
```bash
source ~/.zshrc && ./gradlew build
```

---

## 🔍 주요 기술 스택

- **Java 17**
- **Spring Boot 3.4.1**
- **PostgreSQL** (주 데이터베이스)
- **MongoDB** (도큐먼트 저장)
- **Redis** (토큰 관리)
- **JWT** (인증)
- **Swagger** (API 문서화)
- **Gradle** (멀티모듈 빌드)

---

이 가이드라인을 참고하여 일관된 코드를 작성하세요.