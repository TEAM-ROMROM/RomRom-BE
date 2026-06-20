package com.romrom.chat.repository.mongo;

import java.time.LocalDateTime;
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
}
