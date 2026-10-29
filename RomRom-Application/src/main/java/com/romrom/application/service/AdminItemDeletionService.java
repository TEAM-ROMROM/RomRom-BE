package com.romrom.application.service;

import com.romrom.ai.service.EmbeddingService;
import com.romrom.common.constant.ItemAdminDeleteReason;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.mongo.LikeHistoryRepository;
import com.romrom.item.repository.postgres.HiddenItemRepository;
import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.item.service.ItemService;
import com.romrom.notification.event.ItemDeletedByAdminEvent;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 물품 soft delete: 단건(거래 취소·이미지·임베딩·좋아요·숨김 정리 + 알림), 회원 보유 물품 일괄, 소유자 검증 단건
 * 다른 빈에서 프록시로 호출 > 건별 독립 트랜잭션 (AdminMemberSanctionService.bulkDeleteItems)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminItemDeletionService {

  private final ItemRepository itemRepository;
  private final ItemImageRepository itemImageRepository;
  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final LikeHistoryRepository likeHistoryRepository;
  private final HiddenItemRepository hiddenItemRepository;
  private final EmbeddingService embeddingService;
  private final ItemService itemService;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 관리자용 물품 Soft Delete
   * - FK 제약 위반 방지를 위해 관련 TradeRequestHistory를 Hard Delete하지 않고 CANCELED 상태로 변경
   * - trade_request_history, chat_room, chat_message 데이터는 보존 (Phase 2에서 UI/정책 처리)
   * - 영향받는 거래 상대방에게 FCM 알림 발송 (트랜잭션 커밋 후 비동기 처리)
   */
  @Transactional
  public void deleteItemByAdmin(UUID itemId, ItemAdminDeleteReason adminDeleteReason, String adminDeleteDetail) {
    Item item = itemRepository.findById(itemId)
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    if (item.getIsDeleted()) {
      log.warn("이미 삭제된 물품에 대한 관리자 삭제 요청 무시: itemId={}", itemId);
      return;
    }

    // 1. 알림 대상 회원 수집 (상태 변경 전에 먼저 조회)
    List<TradeRequestHistory> relatedTradeHistories =
        tradeRequestHistoryRepository.findAllWithMembersByItemId(itemId);
    List<UUID> affectedMemberIds = relatedTradeHistories.stream()
        .flatMap(tradeHistory -> Stream.of(
            tradeHistory.getGiveItem().getMember().getMemberId(),
            tradeHistory.getTakeItem().getMember().getMemberId()
        ))
        .distinct()
        .collect(Collectors.toList());

    // 물품 소유자도 알림 대상에 포함
    UUID itemOwnerId = item.getMember().getMemberId();
    if (!affectedMemberIds.contains(itemOwnerId)) {
      affectedMemberIds.add(itemOwnerId);
    }

    // 2. 관련 TradeRequestHistory CANCELED 처리 (chat_room FK 제약 위반 방지)
    tradeRequestHistoryRepository.cancelAllActiveByItemId(itemId);

    // 3. 이미지 파일 삭제 (외부 스토리지 + DB 레코드)
    deleteItemImagesWithStorageFiles(item);

    // 4. 임베딩 삭제
    embeddingService.deleteItemEmbedding(itemId);

    // 5. 좋아요 내역 삭제
    likeHistoryRepository.deleteAllByItemId(itemId);

    // 6. 숨긴 물품 이력 삭제
    hiddenItemRepository.deleteAllByItemItemId(itemId);

    // 6. Soft Delete + 삭제 사유 저장
    item.setIsDeleted(true);
    item.setAdminDeleteReason(adminDeleteReason);
    item.setAdminDeleteDetail(adminDeleteDetail);

    // 8. 알림 이벤트 발행 (트랜잭션 커밋 후 비동기 처리)
    if (!affectedMemberIds.isEmpty()) {
      eventPublisher.publishEvent(new ItemDeletedByAdminEvent(affectedMemberIds, item.getItemName(), adminDeleteReason));
    }

    log.info("관리자 물품 Soft Delete 완료: itemId={}, reason={}, canceledTradeHistories={}, notifiedMembers={}",
        itemId, adminDeleteReason, relatedTradeHistories.size(), affectedMemberIds.size());
  }

  /**
   * 회원 강제탈퇴 등 관리자 작업 시 보유 물품 일괄 soft delete
   * - 관련 TradeRequestHistory도 CANCELED로 일괄 변경 (FK 위반 방지)
   * - 임베딩/좋아요/이미지/숨김 이력 정리
   */
  @Transactional
  public int softDeleteAllByMember(UUID memberId) {
    List<UUID> ownedItemIds = itemRepository.findAllIdsByMemberId(memberId);
    if (ownedItemIds.isEmpty()) {
      return 0;
    }
    for (UUID ownedItemId : ownedItemIds) {
      tradeRequestHistoryRepository.cancelAllActiveByItemId(ownedItemId);
      likeHistoryRepository.deleteAllByItemId(ownedItemId);
      hiddenItemRepository.deleteAllByItemItemId(ownedItemId);
    }
    embeddingService.deleteItemEmbeddings(ownedItemIds);
    itemRepository.softDeleteAllByMemberId(memberId);
    log.info("회원 강제탈퇴 - 보유 물품 일괄 soft delete: memberId={}, count={}", memberId, ownedItemIds.size());
    return ownedItemIds.size();
  }

  /**
   * 관리자 일괄 물품 삭제 (개별 결과 반환용 단건 처리)
   * - 소유자 검증 후 soft delete
   * - 실패 케이스는 caller에서 처리하도록 boolean 반환
   */
  @Transactional
  public void softDeleteByAdmin(UUID itemId, UUID expectedOwnerMemberId, ItemAdminDeleteReason adminDeleteReason, String adminDeleteDetail) {
    Item targetItem = itemRepository.findById(itemId)
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
    if (!targetItem.getMember().getMemberId().equals(expectedOwnerMemberId)) {
      throw new CustomException(ErrorCode.INVALID_ITEM_OWNER);
    }
    if (targetItem.getIsDeleted()) {
      return;
    }
    deleteItemByAdmin(itemId, adminDeleteReason, adminDeleteDetail);
  }

  /**
   * ItemImage DB 레코드 hardDelete 및 저장소 파일 삭제
   */
  private void deleteItemImagesWithStorageFiles(Item item) {
    List<String> imageUrlsToDelete = itemImageRepository.findAllByItem(item).stream()
        .map(ItemImage::getImageUrl)
        .toList();
    itemImageRepository.deleteAllByItem(item);
    itemService.deleteStorageImagesAfterCommit(imageUrlsToDelete, item.getItemId());
  }
}
