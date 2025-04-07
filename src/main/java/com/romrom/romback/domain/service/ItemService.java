package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.constant.LikeContentType;
import com.romrom.romback.domain.object.dto.ItemRequest;
import com.romrom.romback.domain.object.dto.ItemResponse;
import com.romrom.romback.domain.object.dto.LikeRequest;
import com.romrom.romback.domain.object.dto.LikeResponse;
import com.romrom.romback.domain.object.dto.LikeResponse.LikeStatusEnum;
import com.romrom.romback.domain.object.mongo.LikeHistory;
import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.ItemImage;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.repository.mongo.LikeHistoryRepository;
import com.romrom.romback.domain.repository.postgres.ItemRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    if(member.getMemberId().equals(item.getMember().getMemberId())) {
      log.debug("좋아요 등록 실패 : 본인의 게시물에는 좋아요를 달 수 없음");
      throw new CustomException(ErrorCode.SELF_LIKE_NOT_ALLOWED);
    }

    // 좋아요 존재시 취소 로직
    if (likeHistoryRepository.existsByMemberIdAndItemId(member.getMemberId(), item.getItemId())) {

      log.debug("이미 좋아요를 누른 글에는 좋아요 취소를 진행합니다 : 물품={}", item.getItemId());

      likeHistoryRepository.deleteByMemberIdAndItemId(item.getItemId(), member.getMemberId());
      item.decreaseLikeCount();
      itemRepository.save(item);
      log.debug("좋아요 취소 완료 : likes={}", item.getLikeCount());

      return LikeResponse.builder()
          .likeStatusEnum(LikeStatusEnum.UNLIKE)
          .likeCount(item.getLikeCount())
          .build();
    }

    log.debug("좋아요가 없는 글에는 좋아요 등록을 진행합니다 : 물품={}", item.getItemId());
    likeHistoryRepository.save(LikeHistory.builder()
        .itemId(item.getItemId())
        .memberId(member.getMemberId())
        .likeContentType(LikeContentType.POST)
        .build());

    item.increaseLikeCount();
    itemRepository.save(item);
    log.debug("좋아요 등록 완료 : likes={}", item.getLikeCount());
    return LikeResponse.builder()
        .likeStatusEnum(LikeStatusEnum.LIKE)
        .likeCount(item.getLikeCount())
        .build();
  }

}
