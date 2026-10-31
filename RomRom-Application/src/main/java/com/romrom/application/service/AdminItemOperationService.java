package com.romrom.application.service;

import com.romrom.ai.event.EmbeddingRefreshRequestedEvent;
import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.constant.OriginalType;
import com.romrom.common.entity.postgres.Embedding;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.repository.EmbeddingRepository;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemPromotionHistory;
import com.romrom.item.repository.postgres.ItemPromotionHistoryRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 물품 운영: 운영 상태(롬업 이력·임베딩) 조회, 임베딩 재생성, 롬업 종료
 * 물품 수정·숨김·삭제는 AdminItemService 담당
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminItemOperationService {

  private final ItemRepository itemRepository;
  private final ItemPromotionHistoryRepository itemPromotionHistoryRepository;
  private final EmbeddingRepository embeddingRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 운영 상태 2개: 롬업 이력(최신순), 임베딩 존재·생성 일시
   */
  @Transactional(readOnly = true)
  public AdminResponse getItemOperationStatus(AdminRequest request) {
    Item item = findItem(request);
    List<ItemPromotionHistory> itemPromotionHistories = itemPromotionHistoryRepository.findAllByItemIdForAdmin(item.getItemId());
    Optional<Embedding> latestItemEmbedding = embeddingRepository
        .findFirstByOriginalIdAndOriginalTypeOrderByCreatedDateDesc(item.getItemId(), OriginalType.ITEM);

    return AdminResponse.builder()
        .itemPromotionHistories(itemPromotionHistories)
        .hasItemEmbedding(latestItemEmbedding.isPresent())
        .itemEmbeddingCreatedAt(latestItemEmbedding.map(Embedding::getCreatedDate).orElse(null))
        .build();
  }

  /**
   * 임베딩 재생성 요청 (비동기 리스너가 이름·설명 기준으로 다시 계산, 삭제 물품 제외)
   */
  @Transactional(readOnly = true)
  public AdminResponse regenerateItemEmbedding(AdminRequest request) {
    Item item = findItem(request);
    if (Boolean.TRUE.equals(item.getIsDeleted())) {
      throw new CustomException(ErrorCode.ITEM_NOT_FOUND);
    }
    eventPublisher.publishEvent(new EmbeddingRefreshRequestedEvent(item.getItemId(), OriginalType.ITEM, item.toEmbeddingText()));
    log.info("[admin item] embedding regeneration requested : itemId={}, executorAdminId={}",
        item.getItemId(), request.getExecutorAdminId());
    return AdminResponse.builder().build();
  }

  /**
   * 롬업 즉시 종료 (만료 일시 = 지금, 이력은 유지)
   */
  @Transactional
  public AdminResponse endItemPromotion(AdminRequest request) {
    Item item = findItem(request);
    if (item.getPromotionExpiresAt() == null || item.getPromotionExpiresAt().isBefore(LocalDateTime.now())) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    log.info("[admin item] promotion ended : itemId={}, expiresAt {} -> now, executorAdminId={}",
        item.getItemId(), item.getPromotionExpiresAt(), request.getExecutorAdminId());
    item.setPromotionExpiresAt(LocalDateTime.now());
    itemRepository.save(item);
    return AdminResponse.builder().build();
  }

  private Item findItem(AdminRequest request) {
    if (request.getItemId() == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    return itemRepository.findById(request.getItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
  }
}
