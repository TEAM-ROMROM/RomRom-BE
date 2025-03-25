package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.dto.ItemRequest;
import com.romrom.romback.domain.object.dto.ItemResponse;
import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.ItemImage;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.repository.postgres.ItemRepository;
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
  private final ItemImageService itemImageService;

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
        .tradeOptions(request.getTradeOptions())
        .price(request.getPrice())
        .build();
    itemRepository.save(item);

    // 이미지 업로드 및 ItemImage 엔티티 저장
    List<ItemImage> itemImages = itemImageService.saveItemImages(item, request.getItemImages());

    // 첫 물품 등록 여부가 false 일 경우 true 로 업데이트
    if (!member.getIsFirstItemPosted()) {
      member.setIsFirstItemPosted(true);
    }

    return ItemResponse.builder()
        .member(member)
        .item(item)
        .itemImages(itemImages)
        .build();
  }
}
