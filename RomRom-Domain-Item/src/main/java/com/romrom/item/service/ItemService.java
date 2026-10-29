package com.romrom.item.service;

import com.romrom.ai.service.EmbeddingService;
import com.romrom.ai.service.VertexAiClient;
import com.romrom.common.constant.InteractionType;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.LikeContentType;
import com.romrom.ai.event.EmbeddingRefreshRequestedEvent;
import com.romrom.common.constant.OriginalType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.service.UgcFilterService;
import com.romrom.common.util.LocationUtil;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import com.romrom.item.entity.mongo.LikeHistory;
import com.romrom.item.entity.postgres.HiddenItem;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.repository.mongo.LikeHistoryRepository;
import com.romrom.item.repository.postgres.HiddenItemRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.member.service.MemberBlockService;
import com.romrom.notification.event.ItemLikedEvent;
import com.romrom.storage.dto.StorageRequest;
import com.romrom.storage.service.StorageService;
import com.romrom.storage.util.FileUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemService {

  @Value("${file.domain}")
  private String domain;

  private final ItemRepository itemRepository;
  private final LikeHistoryRepository likeHistoryRepository;
  private final UserInteractionService userInteractionService;
  private final EmbeddingService embeddingService;
  private final VertexAiClient vertexAiClient;
  private final MemberRepository memberRepository;
  private final MemberBlockService memberBlockService;
  private final ApplicationEventPublisher eventPublisher;
  private final UgcFilterService ugcFilterService;
  private final StorageService storageService;
  private final HiddenItemRepository hiddenItemRepository;

  // 물품 등록
  @Transactional
  public ItemResponse postItem(ItemRequest request) {

    Member member = request.getMember();

    ugcFilterService.validate(request.getItemName(), "itemName");
    ugcFilterService.validate(request.getItemDescription(), "itemDescription");

    Item item = Item.builder()
        .member(member)
        .itemImages(new ArrayList<>())
        .itemName(request.getItemName())
        .itemDescription(request.getItemDescription())
        .itemCategory(request.getItemCategory())
        .itemCondition(request.getItemCondition())
        .itemStatus(ItemStatus.AVAILABLE) // 물품 거래 상태 기본값 지정
        .itemTradeOptions(request.getItemTradeOptions())
        .location(LocationUtil.convertToPoint(request.getLongitude(), request.getLatitude()))
        .likeCount(0)
        .price(request.getItemPrice())
        .isAiPredictedPrice(request.getIsAiPredictedPrice())
        .build();
    Item savedItem = itemRepository.save(item);

    // ItemImage 저장
    request.getItemImageUrls().forEach(url -> {
      ItemImage itemImage = ItemImage.builder()
          .item(savedItem)
          .filePath(FileUtil.extractFilePath(domain, url))
          .imageUrl(url)
          .build();
      savedItem.addItemImage(itemImage);
    });

    // 첫 물품 등록 여부 저장 (업데이트 전 상태)
    // null 또는 false인 경우 모두 첫 등록으로 간주
    boolean isReallyFirstPost = (member.getIsFirstItemPosted() == null ||
                                 member.getIsFirstItemPosted() == false);

    // 첫 물품 등록 여부가 null 또는 false 일 경우 true 로 업데이트
    if (isReallyFirstPost) {
      member.setIsFirstItemPosted(true);
      // CustomUserDetails의 member는 비영속 상태이기 떄문에, save 메서드 필요
      memberRepository.save(member);
    }

    // 아이템 임베딩 생성 (커밋 후 비동기)
    eventPublisher.publishEvent(new EmbeddingRefreshRequestedEvent(
        savedItem.getItemId(), OriginalType.ITEM, savedItem.toEmbeddingText()));

    return ItemResponse.builder()
        .item(savedItem)
        .isFirstItemPosted(isReallyFirstPost)
        .build();
  }

  // 물품 수정
  @Transactional
  public void updateItem(ItemRequest request) {
    // 1) 기존 아이템 조회 및 권한 체크
    Item item = findItemAndAuthorizeByRequest(request);

    ugcFilterService.validate(request.getItemName(), "itemName");
    ugcFilterService.validate(request.getItemDescription(), "itemDescription");

    // 2) 필드 업데이트
    applyRequestToItem(request, item);

    // 3) 임베딩 갱신 (커밋 후 비동기)
    eventPublisher.publishEvent(new EmbeddingRefreshRequestedEvent(
        item.getItemId(), OriginalType.ITEM, item.toEmbeddingText()));

    // 4) 이미지 업데이트
    // 기존 이미지 URL 목록 보관 (파일 삭제 대상 판별용)
    List<String> existingImageUrls = item.getItemImages().stream()
        .map(ItemImage::getImageUrl)
        .toList();
    List<String> newImageUrls = request.getItemImageUrls();

    // Item & ItemImage 연관관계 제거
    List<ItemImage> itemImages = new ArrayList<ItemImage>(item.getItemImages());
    for (ItemImage itemImage : itemImages) {
      item.removeItemImage(itemImage);
    }
    log.debug("기존 아이템 이미지 삭제 완료: itemId={}", item.getItemId());
    itemRepository.saveAndFlush(item);

    newImageUrls.forEach(url -> {
      ItemImage itemImage = ItemImage.builder()
          .item(item)
          .filePath(FileUtil.extractFilePath(domain, url))
          .imageUrl(url)
          .build();
      item.addItemImage(itemImage);
    });

    itemRepository.save(item);

    // 5) 교체된 이미지의 실제 파일 삭제 (기존 URL 중 새 URL에 없는 것, 커밋 후)
    List<String> removedImageUrls = existingImageUrls.stream()
        .filter(url -> !newImageUrls.contains(url))
        .toList();
    deleteStorageImagesAfterCommit(removedImageUrls, item.getItemId());
  }

  // 물품 삭제
  @Transactional
  public void deleteItem(ItemRequest request) {
    // 1) 기존 아이템 조회 및 권한 체크
    Item item = findItemAndAuthorizeByRequest(request);

    // 2) softDelete 처리 (관련 리소스는 유지)
    item.setIsDeleted(true);
    itemRepository.save(item);
  }

  /**
   * 물품 좋아요 & 취소
   *
   * @param request UUID itemId
   */
  @Transactional
  public ItemResponse likeOrUnlikeItem(ItemRequest request) {
    Member member = request.getMember();
    Item item = findItemById(request.getItemId());

    // 본인 게시물에는 좋아요 달 수 없으므로 예외 처리
    if (member.getMemberId().equals(item.getMember().getMemberId())) {
      log.debug("좋아요 등록 실패 : 본인의 게시물에는 좋아요를 달 수 없음");
      throw new CustomException(ErrorCode.SELF_LIKE_NOT_ALLOWED);
    }
    memberBlockService.verifyNotBlocked(member.getMemberId(), item.getMember().getMemberId());

    // 삭제된 물품에는 좋아요 불가
    if (item.getIsDeleted()) {
      log.debug("삭제된 물품 좋아요 시도 차단: itemId={}", item.getItemId());
      throw new CustomException(ErrorCode.DELETED_ITEM);
    }

    // 좋아요 존재시 취소 로직
    if (likeHistoryRepository.existsByMemberIdAndItemId(member.getMemberId(), item.getItemId())) {
      log.debug("이미 좋아요를 누른 글에는 좋아요 취소를 진행합니다 : 물품={}", item.getItemId());

      likeHistoryRepository.deleteByMemberIdAndItemId(member.getMemberId(), item.getItemId());
      item.decreaseLikeCount();
      Item savedDecresedLikeItem = itemRepository.save(item);
      item.getMember().decreaseTotalLikeCount();

      // 사용자 행동 점수 업데이트
      userInteractionService.updateInteractionScore(
          member.getMemberId(),
          item.getItemCategory(),
          InteractionType.UNLIKE
      );

      log.debug("좋아요 취소 완료 : likes={}", item.getLikeCount());

      return ItemResponse.builder()
          .item(savedDecresedLikeItem)
          .isLiked(false)
          .build();
    }

    log.debug("좋아요가 없는 글에는 좋아요 등록을 진행합니다 : 물품={}", item.getItemId());
    likeHistoryRepository.save(LikeHistory.builder()
        .itemId(item.getItemId())
        .memberId(member.getMemberId())
        .likeContentType(LikeContentType.ITEM)
        .build());

    item.increaseLikeCount();
    Item savedIncreasedLikeItem = itemRepository.save(item);
    item.getMember().increaseTotalLikeCount();

    // 사용자 행동 점수 업데이트
    userInteractionService.updateInteractionScore(
        member.getMemberId(),
        item.getItemCategory(),
        InteractionType.LIKE
    );

    log.debug("좋아요 등록 완료 : likes={}", item.getLikeCount());

    // 좋아요 알림 발송
    ItemLikedEvent event = new ItemLikedEvent(
      item.getMember().getMemberId(),
      item.getItemId(),
      item.getItemName(),
      member.getNickname()
    );
    eventPublisher.publishEvent(event);
    return ItemResponse.builder()
        .item(savedIncreasedLikeItem)
        .isLiked(true)
        .build();
  }

  @Transactional
  public void deleteAllRelatedItemInfoByMemberId(UUID memberId) {
    // 해당 멤버의 모든 아이템 ID 조회
    List<UUID> itemIds = itemRepository.findAllIdsByMemberId(memberId);

    // 각 아이템의 임베딩 일괄 삭제
    if (!itemIds.isEmpty()) {
      embeddingService.deleteItemEmbeddings(itemIds);
      likeHistoryRepository.deleteAllByItemIdIn(itemIds);
    }

    // 숨긴 물품 이력 삭제
    hiddenItemRepository.deleteAllByMemberMemberId(memberId);

    // 아이템 Soft Delete 처리
    itemRepository.softDeleteAllByMemberId(memberId);
    log.debug("회원 탈퇴에 따른 임베딩, 좋아요 이력, 이미지 및 아이템 일괄 정리 완료: memberId={}, count={}", memberId, itemIds.size());
  }

  /**
   * 제품 설명을 기반으로 중고 거래 가격 예측
   *
   * @param itemRequest 제품 설명 요청 객체
   * @return 예측된 가격 (KRW, 정수)
   */
  public int predictItemPrice(ItemRequest itemRequest) {
    try {
      // 필요한 정보만 추출해서 텍스트 생성
      String itemName = itemRequest.getItemName();
      String description = itemRequest.getItemDescription();
      String condition = itemRequest.getItemCondition() != null ? itemRequest.getItemCondition().name() : "";

      // Vertex AI에 보낼 문장 조합
      StringBuilder promptBuilder = new StringBuilder();
      if (itemName != null) {
        promptBuilder.append(itemName).append(", ");
      }
      if (description != null) {
        promptBuilder.append(description).append(", ");
      }
      if (!condition.isEmpty()) {
        promptBuilder.append("상태: ").append(condition);
      }

      String prompt = promptBuilder.toString();

      log.debug("중고가 예측 요청 문장: {}", prompt);
      return vertexAiClient.getItemPricePrediction(prompt);

    } catch (Exception e) {
      log.error("가격 예측 실패: {}", itemRequest, e);
      throw new CustomException(ErrorCode.ITEM_VALUE_PREDICTION_FAILED);
    }
  }

  @Transactional
  public ItemResponse updateItemStatus(ItemRequest request) {
    Item item = findItemAndAuthorizeByRequest(request);

    item.setItemStatus(request.getItemStatus());
    return ItemResponse.builder()
        .item(itemRepository.save(item))
        .isLiked(isLikedByMember(item, request.getMember()))
        .build();
  }

  @Transactional
  public void hideItem(ItemRequest request) {
    UUID memberId = request.getMember().getMemberId();
    UUID itemId = request.getItemId();

    Item item = findItemById(itemId);

    if (item.getMember().getMemberId().equals(memberId)) {
      throw new CustomException(ErrorCode.INVALID_ITEM_OWNER);
    }

    if (hiddenItemRepository.existsByMemberMemberIdAndItemItemId(memberId, itemId)) {
      throw new CustomException(ErrorCode.ALREADY_HIDDEN_ITEM);
    }

    hiddenItemRepository.save(HiddenItem.builder()
        .member(request.getMember())
        .item(item)
        .build());
  }

  @Transactional
  public void unhideItem(ItemRequest request) {
    UUID memberId = request.getMember().getMemberId();
    UUID itemId = request.getItemId();

    if (!hiddenItemRepository.existsByMemberMemberIdAndItemItemId(memberId, itemId)) {
      throw new CustomException(ErrorCode.HIDDEN_ITEM_NOT_FOUND);
    }

    hiddenItemRepository.deleteByMemberMemberIdAndItemItemId(memberId, itemId);
  }

  public Item findItemById(UUID itemId) {
    return itemRepository.findByItemIdAndIsDeletedFalse(itemId)
        .orElseThrow(() -> {
          log.error("요청된 id에 해당하는 물품을 찾을 수 없습니다. 요청id: {}", itemId);
          return new CustomException(ErrorCode.ITEM_NOT_FOUND);
        });
  }

  /**
   * 저장소 이미지 파일 삭제를 커밋 이후로 예약 (롤백 시 DB가 이미 지운 파일을 가리키는 문제 방지)
   * 트랜잭션 밖 호출이면 즉시 삭제
   */
  public void deleteStorageImagesAfterCommit(List<String> imageUrls, UUID itemId) {
    if (imageUrls.isEmpty()) {
      return;
    }
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      deleteStorageImages(imageUrls, itemId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        deleteStorageImages(imageUrls, itemId);
      }
    });
  }

  //-------------------------------- private 메서드 --------------------------------//

  /**
   * 저장소 이미지 파일 삭제, 실패 시 경고만 (고아 파일은 스케줄러에서 정리)
   */
  private void deleteStorageImages(List<String> imageUrls, UUID itemId) {
    try {
      storageService.deleteImages(StorageRequest.builder().imageUrls(imageUrls).build());
      log.debug("물품 이미지 파일 삭제 완료: itemId={}, deletedCount={}", itemId, imageUrls.size());
    } catch (Exception storageDeleteException) {
      log.warn("물품 이미지 파일 삭제 실패 (스케줄러에서 정리 예정): itemId={}, error={}", itemId, storageDeleteException.getMessage());
    }
  }

  /**
   * 아이템 조회 및 권한 체크
   */
  private Item findItemAndAuthorizeByRequest(ItemRequest request) {
    Member member = request.getMember();
    Item item = findItemById(request.getItemId());
    if (!item.getMember().getMemberId().equals(member.getMemberId())) {
      throw new CustomException(ErrorCode.INVALID_ITEM_OWNER);
    }
    return item;
  }

  /**
   * ItemRequest 값을 Item 엔티티에 적용
   */
  private void applyRequestToItem(ItemRequest request, Item item) {
    item.setItemName(request.getItemName());
    item.setItemDescription(request.getItemDescription());
    item.setItemCategory(request.getItemCategory());
    item.setItemCondition(request.getItemCondition());
    item.setItemTradeOptions(request.getItemTradeOptions());
    item.setLocation(LocationUtil.convertToPoint(request.getLongitude(), request.getLatitude()));
    item.setIsAiPredictedPrice(request.getIsAiPredictedPrice());
    item.setPrice(request.getItemPrice());
  }

  private boolean isLikedByMember(Item item, Member member) {
    return likeHistoryRepository.existsByMemberIdAndItemId(member.getMemberId(), item.getItemId());
  }
}
