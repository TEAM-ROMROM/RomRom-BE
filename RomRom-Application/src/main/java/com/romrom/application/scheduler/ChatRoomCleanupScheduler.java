package com.romrom.application.scheduler;

import com.romrom.application.service.ChatRoomArchiveService;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.chat.service.ChatRoomService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * soft-delete된 채팅방 자동 정리 스케줄러 (#750)
 * - deletedAt 으로부터 30일 경과한 방을 아카이브(.json.gz) 후 물리 삭제
 * - 아카이브 실패 시 해당 방은 물리삭제를 건너뛰어, 다음 배치가 재시도 (백업 없는 삭제 방지)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRoomCleanupScheduler {

  // soft-delete 후 물리삭제 전 최소 보존 기간
  private static final int ARCHIVE_RETENTION_DAYS = 30;

  private final ChatRoomRepository chatRoomRepository;
  private final ChatRoomArchiveService chatRoomArchiveService;
  private final ChatRoomService chatRoomService;

  // TODO: 운영 검증 후 활성화 (OrphanImageCleanupScheduler 동일 패턴)
//  @Scheduled(cron = "0 0 4 * * *")  // 매일 새벽 4시
  public void cleanupDeletedChatRooms() {
    log.info("채팅방 정리 스케줄러 시작");

    try {
      LocalDateTime cleanupThreshold = LocalDateTime.now().minusDays(ARCHIVE_RETENTION_DAYS);
      List<ChatRoom> cleanupTargets = chatRoomRepository.findCleanupTargets(cleanupThreshold);
      log.info("채팅방 정리 대상 수: {}", cleanupTargets.size());

      int archivedCount = 0;
      int deletedCount = 0;

      for (ChatRoom targetRoom : cleanupTargets) {
        try {
          // 아카이브 먼저 — 실패 시 예외를 던져 물리삭제 단계를 건너뜀
          chatRoomArchiveService.archiveToFile(targetRoom);
          archivedCount++;

          chatRoomService.physicalDelete(targetRoom.getChatRoomId());
          deletedCount++;
        } catch (Exception cleanupException) {
          // 방 단위 실패는 warn 후 계속 진행 (다음 배치에서 재시도)
          log.warn("채팅방 정리 실패(다음 배치 재시도): roomId={}, error={}",
              targetRoom.getChatRoomId(), cleanupException.getMessage());
        }
      }

      log.info("채팅방 정리 스케줄러 완료: 대상={}, 아카이브={}, 삭제={}",
          cleanupTargets.size(), archivedCount, deletedCount);
    } catch (Exception schedulerException) {
      log.error("채팅방 정리 스케줄러 실행 중 오류 발생: {}", schedulerException.getMessage(), schedulerException);
    }
  }
}
