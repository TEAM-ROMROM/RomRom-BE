package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.dto.ItemRequest;
import com.romrom.romback.domain.object.dto.ItemResponse;
import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.ItemImage;
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
    Item item = Item.builder()
        .member(request.getMember())
        .itemName(request.getItemName())
        .itemDescription(request.getItemDescription())
        .itemCategory(request.getItemCategory())
        .itemCondition(request.getItemCondition())
        .tradeOptions(request.getTradeOptions())
        .price(request.getPrice())
        .build();
    itemRepository.save(item);

    List<ItemImage> itemImages = itemImageService.saveItemImages(item, request.getItemImages());

    return ItemResponse.builder()
        .member(request.getMember())
        .item(item)
        .itemImages(itemImages)
        .build();
  }
}
