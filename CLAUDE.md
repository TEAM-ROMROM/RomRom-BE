# 🎯 RomRom 프로젝트 개발 가이드

## 🔍 주요 기술 스택

- **Java 17**
- **Spring Boot 3.4.1**
- **PostgreSQL** (주 데이터베이스)
- **MongoDB** (도큐먼트 저장)
- **Redis** (토큰 관리)
- **JWT** (인증)
- **Swagger** (API 문서화)
- **Gradle** (멀티모듈 빌드)
- **AdminLTE + Bootstrap 5** (관리자 UI)
- **Vanilla JavaScript + fetch API** (프론트엔드, jQuery 미사용)

## 📁 멀티모듈 구조

```
RomRom-BE/
├── RomRom-Common/          # 공통 유틸리티, DTO, 상수
├── RomRom-Domain-Member/   # 회원 도메인
├── RomRom-Domain-Auth/     # 인증 도메인  
├── RomRom-Domain-Item/     # 물품 도메인
├── RomRom-Domain-Chat/     # 채팅 도메인
├── RomRom-Domain-AI/       # AI 도메인
├── RomRom-Domain-Report/   # 신고 도메인
├── RomRom-Domain-Notification/ # 알림 도메인
├── RomRom-Domain-Storage/  # 저장소 도메인
├── RomRom-Application/     # 애플리케이션 서비스 레이어
└── RomRom-Web/             # 웹 레이어 (컨트롤러, 설정)
```

## 🎯 핵심 개발 원칙

### 1. 멀티모듈 의존성 규칙
- **Domain → Common**: 각 도메인은 Common 모듈만 의존
- **Application → Domain + Common**: 크로스 도메인 로직 처리
- **Web → Application + Domain + Common**: 웹 레이어는 모든 모듈 접근 가능
- **Domain 간 직접 의존 금지**: 반드시 Application 레이어를 통해 연동

### 2. 패키지 구조 표준
```java
com.romrom.{domain}/
├── entity/           # 엔티티 클래스
├── repository/       # 리포지토리 인터페이스/구현체
├── service/          # 도메인 서비스
├── dto/             # DTO 클래스
└── constant/        # 도메인별 상수
```

### 3. 네이밍 컨벤션
- **Entity**: `Member`, `Item`, `Chat`
- **DTO**: `MemberRequest`, `MemberResponse`, `ItemRequest`
- **Service**: `MemberService`, `ItemService`
- **Repository**: `MemberRepository`, `ItemRepository`
- **Controller**: `MemberController`, `AdminController`

## 🔧 개발 패턴

### 4. 서비스 레이어 패턴
```java
// ✅ 도메인 서비스 - 단일 도메인 로직만 처리
@Service
@RequiredArgsConstructor
public class MemberService {
    public MemberResponse createMember(MemberRequest request) {
        // 단일 도메인 로직만 처리
    }
}

// ✅ 애플리케이션 서비스 - 크로스 도메인 로직 처리
@Service
@RequiredArgsConstructor  
public class MemberApplicationService {
    private final MemberService memberService;
    private final ItemService itemService;
    
    public void deleteMember(MemberRequest request) {
        // 1. Member 관련 데이터 삭제
        memberService.deleteMemberData(request);
        // 2. Item 관련 데이터 삭제 (크로스 도메인)
        itemService.deleteItemsByMember(request.getMemberId());
    }
}
```

### 5. DTO 설계 패턴
```java
// ✅ Request DTO
@Getter @Setter @Builder
public class MemberRequest {
    private String email;
    private String nickname;
    private Member member; // 인증된 사용자 정보
    
    // 도메인 로직 메서드
    public Member toEntity() {
        return Member.builder()
            .email(email)
            .nickname(nickname)
            .build();
    }
}

// ✅ Response DTO  
@Getter @Builder
public class MemberResponse {
    private String memberId;
    private String email;
    private String nickname;
    private LocalDateTime createdDate;
    
    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
            .memberId(member.getMemberId())
            .email(member.getEmail())
            .nickname(member.getNickname())
            .createdDate(member.getCreatedDate())
            .build();
    }
}
```

### 6. 컨트롤러 패턴
```java
// ✅ API 컨트롤러
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController {
    private final MemberApplicationService memberApplicationService;
    
    @PostMapping
    public ResponseEntity<MemberResponse> createMember(
        @RequestBody @Valid MemberRequest request,
        Authentication authentication
    ) {
        // 인증 정보 설정
        Member authenticatedMember = (Member) authentication.getPrincipal();
        request.setMember(authenticatedMember);
        
        // 서비스 호출
        MemberResponse response = memberApplicationService.createMember(request);
        return ResponseEntity.ok(response);
    }
}
```

### 7. 예외 처리 패턴
```java
// ✅ 커스텀 예외 정의 (Common 모듈)
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

// ✅ 글로벌 예외 핸들러 (Web 모듈)
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of(e.getErrorCode()));
    }
}
```

### 8. 관리자 화면 JavaScript 패턴 (AdminLTE + Bootstrap 5 기반)

#### 페이지 초기화 (Vanilla JavaScript 사용)
```javascript
// ✅ 페이지 로드 완료 시 초기화 (jQuery 대신 Vanilla JS)
document.addEventListener('DOMContentLoaded', function() {
    initData();
    addEventHandlers();
});

// ✅ 초기 데이터 로드 함수
function initData() {
    loadItems();  // fetch API로 데이터 로드
}

// ✅ 이벤트 핸들러 등록 (CSP 규칙 준수)
function addEventHandlers() {
    const filterForm = document.getElementById('filterForm');
    if (filterForm) {
        filterForm.addEventListener('submit', function(e) {
            e.preventDefault();
            currentPage = 0;
            loadItems();
        });
    }
    
    const pageSize = document.getElementById('pageSize');
    if (pageSize) {
        pageSize.addEventListener('change', function() {
            currentPageSize = parseInt(this.value);
            currentPage = 0;
            loadItems();
        });
    }
}
```

#### fetch API를 활용한 AJAX 요청
```javascript
// ✅ fetch API 사용 (jQuery $.ajax 대신)
function loadItems(action = 'list') {
    const formData = new FormData();
    formData.append('action', action);
    formData.append('pageNumber', currentPage);
    formData.append('pageSize', currentPageSize);
    
    fetch('/admin/api/items', {
        method: 'POST',
        body: formData
    })
    .then(response => {
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.json();
    })
    .then(data => {
        if (data.isSuccess) {
            renderItems(data);
        } else {
            console.error('API 호출 실패:', data.message);
        }
    })
    .catch(error => {
        console.error('데이터 로딩 오류:', error);
    });
}
```

#### DOM 조작 (Vanilla JavaScript)
```javascript
// ✅ DOM 요소 접근 및 조작
function renderItems(response) {
    const itemsPage = response.items;
    
    // 총 개수 업데이트
    const totalCount = document.getElementById('totalCount');
    if (totalCount) {
        totalCount.textContent = `총 ${itemsPage.totalElements.toLocaleString()}개`;
    }
    
    // 테이블 바디 렌더링
    const tbody = document.getElementById('itemsTableBody');
    if (tbody) {
        tbody.innerHTML = '';
        
        if (itemsPage.content && itemsPage.content.length > 0) {
            itemsPage.content.forEach(function(item) {
                const row = createItemRow(item);
                tbody.insertAdjacentHTML('beforeend', row);
            });
        }
    }
}
```

#### Bootstrap 5 모달 사용
```javascript
// ✅ Bootstrap 5 모달 (jQuery 대신 Vanilla JS)
function showItemDetailModal(item) {
    // 모달 내용 업데이트
    const modalTitle = document.getElementById('itemDetailModalLabel');
    const modalBody = document.querySelector('#itemDetailModal .modal-body');
    
    if (modalTitle) modalTitle.textContent = item.itemName;
    if (modalBody) modalBody.innerHTML = createItemDetailHtml(item);
    
    // Bootstrap 5 모달 표시
    const modal = new bootstrap.Modal(document.getElementById('itemDetailModal'));
    modal.show();
}
```

## 🚫 금지 사항

```java
// ❌ Domain 모듈 간 직접 의존
// MemberService에서 ItemService 직접 호출 금지
@Service
public class MemberService {
    private final ItemService itemService; // 금지!
}

// ❌ 순환 의존성
// Member → Item → Member 순환 참조 금지

// ❌ Common 모듈에서 Domain 의존
// Common에서 Member, Item 등 도메인 엔티티 직접 참조 금지
```

```javascript
// ❌ HTML 인라인 JavaScript (CSP 규칙 위반)
<button onclick="deleteItem()">삭제</button>  // 금지

// ❌ jQuery 사용 (Bootstrap 5에서는 Vanilla JS 사용)
$(document).ready(function() { });  // 금지
$.ajax({ });  // 금지
$('#element').on('click', function() { });  // 금지
```

## ✅ 개발 체크리스트

### 새로운 기능 개발 시
- [ ] 적절한 도메인 모듈에 기능 구현
- [ ] Request/Response DTO 정의
- [ ] 서비스 레이어에서 비즈니스 로직 구현
- [ ] 컨트롤러에서 HTTP 요청/응답 처리
- [ ] 예외 처리 및 검증 로직 추가
- [ ] 트랜잭션 경계 설정 (@Transactional)

### 크로스 도메인 기능 개발 시  
- [ ] Application 레이어에서 구현
- [ ] 각 도메인 서비스 조합하여 사용
- [ ] 트랜잭션 일관성 보장
- [ ] 롤백 시나리오 고려

### 관리자 화면 JavaScript (Bootstrap 5 + Vanilla JS)
- [ ] `document.addEventListener('DOMContentLoaded')` 내부에서 `initData()` 및 `addEventHandlers()` 호출
- [ ] CSP 규칙 준수: HTML 인라인 JavaScript 금지
- [ ] 이벤트 핸들러는 `addEventHandlers()` 함수에서 등록 (`addEventListener` 사용)
- [ ] jQuery 사용 금지 (Vanilla JavaScript 사용)
- [ ] `fetch API` 사용하여 AJAX 요청 처리
- [ ] DOM 조작 시 `document.getElementById()`, `element.textContent` 등 사용
- [ ] Bootstrap 5 모달은 `new bootstrap.Modal()` 사용
- [ ] 동적 HTML 생성 시 템플릿 리터럴 사용
- [ ] 로딩 상태 표시 및 에러 핸들링
- [ ] 페이지네이션 동적 렌더링
- [ ] 세션 만료 시 로그인 페이지 리다이렉트
- [ ] AdminResponse 구조에 맞는 데이터 접근 (`response.items`, `response.members` 등)

### API 문서화
- [ ] Swagger 어노테이션 추가
- [ ] API 명세서 업데이트
- [ ] 예제 요청/응답 작성

## 🔄 개발 워크플로우

1. **이슈 생성**: GitHub Issues에서 작업 내용 정의
2. **브랜치 생성**: `feature/기능명` 또는 `bugfix/버그명`
3. **개발 진행**: 가이드라인에 따라 구현
4. **테스트 작성**: 단위 테스트 및 통합 테스트
5. **코드 리뷰**: Pull Request 생성 및 리뷰
6. **배포**: 메인 브랜치 머지 후 배포

---

이 가이드를 통해 일관성 있고 확장 가능한 RomRom 백엔드를 개발해주세요! 🚀
