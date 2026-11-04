package com.romrom.chat.repository.mongo;

import com.romrom.chat.entity.mongo.ChatAppointmentStatus;
import com.romrom.chat.entity.mongo.ChatMessage;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface ChatMessageRepositoryCustom {

  /**
   * 여러 채팅방의 안 읽은 메시지 수를 한 번의 집계 쿼리로 조회한다.
   * 방마다 읽음 커서(기준 시각)가 다르므로, 방별로 "기준 시각 이후 + 내가 보내지 않은" 메시지를 세어 합산한다.
   *
   * @param readCursorByRoomId 채팅방 ID → 읽음 커서(이 시각 이후 메시지가 안 읽은 메시지)
   * @param memberId           조회 주체(본인이 보낸 메시지는 안 읽은 메시지에서 제외)
   * @return 채팅방 ID → 안 읽은 메시지 수 (메시지가 없는 방은 결과에 포함되지 않음)
   */
  Map<UUID, Long> countUnreadMessagesByRoom(Map<UUID, LocalDateTime> readCursorByRoomId, UUID memberId);

  /**
   * 약속 상태 조건부 원자적 변경 (현재 상태가 허용 목록일 때만 상태·변경 회원·본문 갱신)
   *
   * @return 갱신된 메시지 (조건 불일치 > null, 동시 수락·취소 경합 판정용)
   */
  ChatMessage changeAppointmentStatusIfCurrent(String chatMessageId, Collection<ChatAppointmentStatus> allowedCurrentStatuses,
                                               ChatAppointmentStatus newStatus, UUID changedMemberId, String newContent);

  /**
   * 리마인드 대상 1건 선점: 확정·미발송·미삭제·미숨김 + 약속 시각 (now, remindUntil] > reminderSentAt 기록
   * 블루그린 동시 실행에도 1건은 한 번만 선점
   *
   * @return 선점한 메시지 (대상 없음 > null)
   */
  ChatMessage claimDueAppointmentReminder(LocalDateTime now, LocalDateTime remindUntil);
}
