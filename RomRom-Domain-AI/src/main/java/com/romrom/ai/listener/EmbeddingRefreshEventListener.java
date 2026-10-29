package com.romrom.ai.listener;

import com.romrom.ai.event.EmbeddingRefreshRequestedEvent;
import com.romrom.ai.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 임베딩 갱신 이벤트 처리: 원본 커밋 후 비동기 실행 (외부 AI 호출이 원본 트랜잭션·DB 커넥션을 잡지 않음)
 * 실패해도 원본 저장에는 영향 없음 (best-effort)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EmbeddingRefreshEventListener {

  private final EmbeddingService embeddingService;

  // fallbackExecution: 트랜잭션 밖 발행도 처리
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handleEmbeddingRefreshRequested(EmbeddingRefreshRequestedEvent event) {
    try {
      embeddingService.refreshEmbedding(event.originalId(), event.originalType(), event.sourceText());
    } catch (Exception embeddingRefreshException) {
      log.error("임베딩 갱신 실패: originalId={}, originalType={}", event.originalId(), event.originalType(),
          embeddingRefreshException);
    }
  }
}
