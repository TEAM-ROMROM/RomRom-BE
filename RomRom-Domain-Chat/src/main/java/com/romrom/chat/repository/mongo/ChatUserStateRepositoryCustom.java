package com.romrom.chat.repository.mongo;

import com.romrom.chat.entity.mongo.ChatUserState;
import java.time.LocalDateTime;
import java.util.UUID;

public interface ChatUserStateRepositoryCustom {

  /**
   * 아직 removed 표시되지 않은(removedAt == null) 내 상태를 원자적으로 removed 표시한다.
   * 단일 문서 findAndModify 연산이므로 동시 요청에서도 read-modify-write 경합이 발생하지 않는다.
   *
   * @return 갱신된 ChatUserState (이미 removed 상태였으면 조건 불일치로 null)
   */
  ChatUserState markRemovedIfNotRemoved(UUID chatRoomId, UUID memberId);

  /**
   * 현재 채팅방에 접속 중(leftAt == null)인 고유 회원 수를 센다.
   * 한 회원이 여러 채팅방에 동시에 접속 중일 수 있으므로 memberId 기준 distinct로 집계한다.
   *
   * @return 채팅 온라인 고유 회원 수
   */
  long countOnlineChatMembers();

  /**
   * 내 방 알림 끈 시각만 원자적 갱신 (null > 켜짐) > 갱신된 상태 (없음 > null), 입장·퇴장 저장과 덮어쓰기 경합 최소화
   */
  ChatUserState updateNotificationMutedAt(UUID chatRoomId, UUID memberId, LocalDateTime notificationMutedAt);
}
