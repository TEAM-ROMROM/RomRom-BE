# Changelog

**현재 버전:** 0.0.21  
**마지막 업데이트:** 2025-08-19T09:39:09Z  

---

## [0.0.21] - 2025-08-19

**PR:** #284  

**New Features**
- 실시간 채팅 인프라(WebSocket/STOMP, RabbitMQ) 추가 및 메시징 에러 처리/인터셉터 도입
- 운영용 데이터 시드/벤치마크/리셋 API 추가

**Improvements**
- 아이템 목록·내 아이템 조회 성능 개선
- 아이템 상세 응답에 회원 위도/경도 포함
- 인증 정보에 만료 확인 및 표준 이름(Principal) 제공

**Breaking Changes**
- 아이템 상태에서 RESERVED 제거

**Documentation**
- API 변경 로그 및 README 버전 정보 갱신

**Chores**
- 프로젝트 버전 0.0.21로 상향, 채팅 모듈 빌드/스캔 포함
- 이슈 자동 댓글 GitHub Actions 워크플로 추가

---

## [0.0.12] - 2025-08-12

**PR:** #272  

**New Features**
- 이미지 저장 API 추가: 업로드(/api/image/upload), 삭제(/api/image/delete) 엔드포인트 제공.
- 물품 등록/수정 시 이미지 업로드 대신 이미지 URL 입력 지원.
- 물품에 AI 가격 측정 여부(aiPrice) 저장 및 반환.

**Documentation**
- README에 최신 버전 배지와 CHANGELOG 링크 추가.
- 아이템 API 문서에 이미지 URL 입력 및 aiPrice 내용 반영.

**Chores**
- 자동 버전/CHANGELOG 갱신 및 README 동기화 워크플로우 도입.
- 프로젝트 버전 업데이트(0.0.11).

---

