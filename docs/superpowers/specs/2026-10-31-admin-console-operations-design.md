# 관리자 화면 운영 기능 보강 설계 (#838)

## 목적

운영자가 DB를 직접 열지 않고 관리자 화면만으로 서비스를 운영할 수 있게 한다. 사용자는 질문 없이 바로 진행하도록 위임했다. 아래 결정은 모두 구현자 판단이다.

## 조사 결과 (2026-10-31)

- 서버 API는 많이 있었지만 화면과 연결되지 않은 것이 20개를 넘었다.
  - 회원 상세의 하위 목록 11개
  - 강제탈퇴, 개별 알림 발송
  - 채팅방 관리 4개
  - 점검 모드
- 버그
  - 영구정지 기준값이 서로 달랐다. 화면은 2100년으로 저장하고, 서버 판정은 9999년 이상만 영구정지로 봤다.
  - 정지를 해제할 때 알림 발송·물품 삭제 기록까지 해제 대상으로 골랐다.
  - 회원 목록의 상태가 항상 "활성"으로 나왔다.
  - 회원 상세 API가 물품을 가진 회원에게서 500을 냈다. 다른 스레드에서 읽은 엔티티의 지연 연관을 직렬화하다 실패했다.
- 관리 화면이 아예 없던 데이터: 채팅방 전체, 회원 차단 관계, FCM 토큰, 롬업 이력, 임베딩 상태, 관리자 비밀번호, 공지 수정, 채팅 추천 운영값, 전체 설정값

## 결정

### 서버
- **영구정지 판정**: `AccountStatus.PERMANENT_SUSPENSION_THRESHOLD`(2100-01-01) 이상이면 영구정지로 본다. 기존 저장값 두 종류(2100년, 9999년)를 모두 포함하고, 데이터 이전은 필요 없다.
- **진행 중 정지 조회**: `SanctionHistoryRepository.findLatestActiveSuspension`. 종류가 SUSPEND이거나 비어 있는 기록만 대상으로 한다. 종류가 비어 있는 것은 종류 필드가 생기기 전의 기록이다.
- **상태 변경 경로**
  - 정지 중인 회원을 "활성"으로 바꾸면 정지 해제로 처리한다.
  - "삭제"는 강제탈퇴로 처리한다. 물품·거래 정리와 토큰 무효화가 함께 일어난다.
  - 해제한 관리자는 `liftedByAdminId`에 기록한다.
- **회원 360**: JPA 엔티티를 담는 카드 8개는 요청 스레드에서 조립한다. 지연 연관을 요청 세션(open-in-view)으로 읽기 위해서다. Mongo·집계 카드 5개는 기존처럼 병렬로 조립한다.
- **새 API** (모두 `POST multipart`, `AdminRequest`/`AdminResponse` 단일 DTO)
  - 회원
    - `/members/blocks`, `/members/blocks/delete`
    - `/members/fcm-tokens`, `/members/fcm-tokens/delete`
    - `/members/update-profile`: 닉네임 20자 이하·중복 불가, 사진 초기화
  - 채팅방: `/chat-rooms/list` (상태 ALL·ACTIVE·DELETED, 닉네임·물품명 검색)
  - 공지
    - `/announcements/update`
    - 작성 시 `isPushNotificationEnabled`를 받는다. 기본값은 true로, 기존 동작을 유지한다.
  - 관리자 계정
    - `/account/change-password`
    - 오류는 401이 아니라 400을 쓴다. 401이면 화면이 로그인으로 튕기기 때문이다.
    - 요청 로그(`@LogMonitor`)를 붙이지 않는다.
  - 물품: `/items/operation-status`, `/items/regenerate-embedding`, `/items/end-promotion`
  - 설정
    - `/config/chat-recommendation/get|update`
    - `/config/all/get` (읽기 전용, SMTP 비밀번호는 가림)
- **설정 저장 공통화**: `SystemConfigService.upsertConfigValue`를 공개한다. DB와 Redis에 함께 반영된다.

### 화면
- **단일 원본**
  - enum 표시 이름은 서버 enum의 description을 `AdminEnumLabels`로 모아 `window.AdminLabelSource`로 주입한다. 화면마다 따로 적던 사본은 지웠다.
  - 상태 배지 색은 `AdminBadges` 한 곳에서 관리한다.
  - 정지 모달은 4개 사본을 `fragments/sanction-modal.html`과 `admin-sanction-modal.js` 하나로 합쳤다.
- **공통 JS** (`common.js`)
  - `AdminApi.call`: 서버 오류 메시지를 그대로 보여 준다.
  - `AdminPager`, `AdminLabels`, `AdminBadges`, `AdminSuspension`
- **새 화면·보강**
  - 회원 상세: 요약 카드 6개와 이력 탭 13개를 둔다. 정보 수정, 알림 보내기, 강제탈퇴, 차단 해제, 토큰 삭제를 할 수 있다.
  - 채팅방 관리: 새 메뉴. 대화 내용 보기, 내보내기, 즉시 삭제
  - 관리자 계정: 새 메뉴
  - 물품 상세: 롬업·임베딩 카드
  - 설정: 점검 모드, 채팅 추천 운영값, 전체 설정값, 실제 빌드 버전(`springBoot.buildInfo()`)
  - 목록 사이를 링크로 잇고, 대시보드 바로가기에 필터를 적용한다.

## 하지 않은 것

- **정리 스케줄러 수동 실행**: 채팅방·고아 이미지 정리는 코드에 "운영 검증 후 활성화"로 막혀 있다. 이미지 삭제는 되돌릴 수 없어서 이번에 열지 않았다.
- **삭제된 물품 복구**: 삭제할 때 이미지와 임베딩이 실제로 지워지므로 복구할 수 없다.
- **임의의 거래 상태 지정**: 강제 완료·취소만 유지했다. 상태 전이 규칙을 우회하면 채팅방·후기 정합성이 깨진다.
- **관리자 추가**: 관리자 계정은 하나로 운영한다.
- **감사 기록(처리 관리자) 일괄 보강**: 물품 수정·숨김, 거래 강제 처리, 설정 변경, 신고 상태 변경은 아직 처리 관리자를 기록하지 않는다. 후속 과제다.
