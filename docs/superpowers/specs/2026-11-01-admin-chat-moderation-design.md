# 관리자 채팅 관리 운영 조치 설계 (#711)

## 배경
- #838에서 채팅방 전체 목록·대화 보기·내보내기·즉시 삭제가 생겼다.
- 신고된 대화에 대한 운영 조치(메시지 가리기, 방 닫기, 회원 내보내기)는 아직 없다.

## 범위
| 기능 | API (`POST /api/admin/...`, multipart) | 동작 |
|---|---|---|
| 메시지 페이지 조회 | `chat-rooms/messages/list` | 최신순 Slice, 숨김 메시지도 원본 + `isHidden` |
| 메시지 숨김 | `chat-rooms/messages/hide` | `ChatMessage.hiddenAt`·`hiddenByAdminId` 기록, 원본 보존 |
| 메시지 숨김 해제 | `chat-rooms/messages/unhide` | 숨김 정보 제거 |
| 채팅방 강제 종료 | `chat-rooms/force-close` | 진행 중 거래 취소 > soft delete (`deletedAt`) |
| 회원 강제 퇴장 | `chat-rooms/members/force-leave` | 회원 직접 나가기와 같은 처리 (`handleRoomExit`) |

- 경로 접두사는 이슈 초안의 `/chats/` 대신 기존 `/chat-rooms/`를 따른다.
- 조치마다 대상 회원 `SanctionHistory`에 감사 기록을 남긴다.
  - `SanctionType`: `CHAT_MESSAGE_HIDE`, `CHAT_MESSAGE_UNHIDE`, `CHAT_ROOM_FORCE_CLOSE`, `CHAT_ROOM_FORCE_LEAVE`
  - 강제 종료는 참여자 2명 모두 기록한다. 발신자가 없는 시스템 메시지는 기록하지 않는다.
  - 사유는 `chatAdminActionReason`(선택)으로 받는다.

## 회원 쪽 정책
- 숨김 메시지는 회원 조회 경로 2곳에서 마스킹한다: 메시지 목록, 채팅방 목록의 마지막 메시지.
  - `content`는 "관리자에 의해 가려진 메시지입니다."로 바꾸고, 이미지·위치는 뺀다.
  - 메시지를 빼지 않고 가리는 이유: 읽음 커서·안 읽은 수·페이지 경계를 바꾸지 않기 위해서다.
  - AI 추천도 마스킹된 목록을 입력으로 받는다.
- WebSocket으로 이미 보낸 메시지는 회수하지 않는다. 새 이벤트 타입도 만들지 않는다.
  - 기존 앱이 모르는 타입을 받으면 오동작할 위험이 있다.
- 메시지 전송 차단 2개를 추가한다. 오류 코드는 모두 `CANNOT_SEND_MESSAGE_TO_CLOSED_CHATROOM`이다.
  - 종료된 방(`deletedAt`): 양쪽 퇴장, 관리자 강제 종료
  - 본인이 나간 방(`removedAt`): 직접 나가기, 관리자 강제 퇴장
  - 기존에는 열린 소켓으로 계속 보낼 수 있었다.

## 하지 않는 것
- 신고된 채팅방 필터: 신고 도메인에 채팅방 연결이 없어서 제외했다.
- 거래 연결 채팅방 필터: 모든 채팅방이 거래에 연결돼 있어서 의미가 없다.
- 숨김 메시지 실시간 회수, 푸시 회수
