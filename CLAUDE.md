# RomRom-BE 프로젝트 컨벤션

## 전체 API 컨벤션

### Response 원칙
- 프로젝트 전체적으로 Entity 객체를 DTO로 변환하지 않고 DB 값 그대로 Response에 담아서 전송
- 별도의 data class 변환 없이 JPA Entity를 직접 응답에 포함하는 것이 기본 원칙
- 일부 예외 케이스를 제외하면 Entity → DTO 매핑 없이 직접 반환

## Admin API 컨벤션

### DTO 네이밍
- Admin 관련 Controller는 하나의 Request와 하나의 Response로 관리
- 네이밍: `Admin{도메인}Request`, `Admin{도메인}Response`
- 예: `AdminReportRequest`, `AdminReportResponse`

### Response 구조
- 전체 원칙과 동일하게 Entity 객체를 그대로 Response에 포함
- 목록 조회 시 Page 정보(totalPages, totalElements, currentPage)는 Response에 포함

### Action 기반 API 패턴
- Admin API는 단일 엔드포인트에 `action` 파라미터로 동작을 구분
- 예: `POST /admin/api/reports` → action: `item-list`, `member-list`, `update-status` 등
