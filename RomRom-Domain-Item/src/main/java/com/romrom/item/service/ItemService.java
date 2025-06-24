package com.romrom.item.service;

import com.romrom.common.constant.LikeContentType;
import com.romrom.common.constant.LikeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.dto.ItemDetailResponse;
import com.romrom.item.dto.ItemFilteredRequest;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import com.romrom.item.dto.LikeRequest;
import com.romrom.item.dto.LikeResponse;
import com.romrom.item.entity.mongo.LikeHistory;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.repository.mongo.LikeHistoryRepository;
import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.common.service.EmbeddingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemService {

  private final ItemRepository itemRepository;
  private final ItemCustomTagsService itemCustomTagsService;
  private final ItemImageService itemImageService;
  private final LikeHistoryRepository likeHistoryRepository;
  private final EmbeddingService embeddingService;
  private final ItemImageRepository itemImageRepository;

  // 물품 등록
  @Transactional
  public ItemResponse postItem(ItemRequest request) {

    Member member = request.getMember();

    // Item 엔티티 생성 및 저장
    Item item = Item.builder()
        .member(member)
        .itemName(request.getItemName())
        .itemDescription(request.getItemDescription())
        .itemCategory(request.getItemCategory())
        .itemCondition(request.getItemCondition())
        .itemTradeOptions(request.getItemTradeOptions())
        .price(request.getItemPrice())
        .build();
    itemRepository.save(item);

    // 커스텀 태그 서비스 코드 추가
    List<String> customTags = itemCustomTagsService.updateTags(item.getItemId(), request.getItemCustomTags());

    // 이미지 업로드 및 ItemImage 엔티티 저장
    List<ItemImage> itemImages = itemImageService.saveItemImages(item, request.getItemImages());

    // 첫 물품 등록 여부가 false 일 경우 true 로 업데이트
    if (!member.getIsFirstItemPosted()) {
      member.setIsFirstItemPosted(true);
    }

    // 아이템 임베딩 값 저장
    embeddingService.generateAndSaveItemEmbedding(item);

    return ItemResponse.builder()
        .item(item)
        .itemImages(itemImages)
        .itemCustomTags(customTags)
        .build();
  }


  // 좋아요 등록 및 취소
  @Transactional
  public LikeResponse likeOrUnlikeItem(LikeRequest request) {

    Member member = request.getMember();
    Item item = itemRepository.findById(request.getItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    // 본인 게시물에는 좋아요 달 수 없으므로 예외 처리
    if (member.getMemberId().equals(item.getMember().getMemberId())) {
      log.debug("좋아요 등록 실패 : 본인의 게시물에는 좋아요를 달 수 없음");
      throw new CustomException(ErrorCode.SELF_LIKE_NOT_ALLOWED);
    }

    // 좋아요 존재시 취소 로직
    if (likeHistoryRepository.existsByMemberIdAndItemId(member.getMemberId(), item.getItemId())) {

      log.debug("이미 좋아요를 누른 글에는 좋아요 취소를 진행합니다 : 물품={}", item.getItemId());

      likeHistoryRepository.deleteByMemberIdAndItemId(member.getMemberId(), item.getItemId());
      item.decreaseLikeCount();
      itemRepository.save(item);
      log.debug("좋아요 취소 완료 : likes={}", item.getLikeCount());

      return LikeResponse.builder()
          .likeStatus(LikeStatus.UNLIKE)
          .likeCount(item.getLikeCount())
          .build();
    }

    log.debug("좋아요가 없는 글에는 좋아요 등록을 진행합니다 : 물품={}", item.getItemId());
    likeHistoryRepository.save(LikeHistory.builder()
        .itemId(item.getItemId())
        .memberId(member.getMemberId())
        .likeContentType(LikeContentType.ITEM)
        .build());

    item.increaseLikeCount();
    itemRepository.save(item);
    log.debug("좋아요 등록 완료 : likes={}", item.getLikeCount());
    return LikeResponse.builder()
        .likeStatus(LikeStatus.LIKE)
        .likeCount(item.getLikeCount())
        .build();
  }

  /**
   * 물품 목록 조회
   *
   * @param request 필터링 및 페이징 요청 정보
   * @return 페이지네이션된 물품 응답
   */
  @Transactional(readOnly = true)
  public Page<ItemDetailResponse> getItemsSortsByCreatedDate(ItemFilteredRequest request) {
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize()
    );

    // 최신순으로 정렬된 Item 페이지 조회
    Page<Item> itemPage = itemRepository.findAllByOrderByCreatedDateDesc(pageable);

    // Item 페이지를 ItemDetailResponse 페이지로 변환
    return itemPage.map(item -> {
      List<ItemImage> itemImages = itemImageRepository.findByItem(item);
      List<String> customTags = itemCustomTagsService.getTags(item.getItemId());
      return ItemDetailResponse.from(item, itemImages, customTags);
    });
  }
}
