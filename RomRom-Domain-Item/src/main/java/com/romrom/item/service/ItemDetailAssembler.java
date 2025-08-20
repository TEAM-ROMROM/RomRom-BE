package com.romrom.item.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.dto.ItemDetail;
import com.romrom.item.entity.mongo.ItemCustomTags;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ItemDetailAssembler {

  private final ItemRepository itemRepository;
  private final ItemImageRepository itemImageRepository;
  private final ItemCustomTagsService itemCustomTagsService;

  /**
   * 모든 아이템에 대한 상세 정보를 조립합니다.
   * @param itemPage 아이템 페이지
   * @return 아이템 상세 정보 페이지
   */
  public Page<ItemDetail> assembleForAllItems(Page<Item> itemPage) {
    LookupBundle lookups = loadLookups(itemPage.getContent().stream().map(Item::getItemId).toList());
    return itemPage.map(item -> ItemDetail.from(
        item,
        lookups.images.getOrDefault(item.getItemId(), List.of()),
        lookups.tags.getOrDefault(item.getItemId(), List.of())));
  }

  private LookupBundle loadLookups(List<UUID> itemIds) {
    Map<UUID, List<ItemImage>> imagesByItemId = itemImageRepository.findAllByItem_ItemIdIn(itemIds)
        .stream()
        .collect(Collectors.groupingBy(img -> img.getItem().getItemId()));

    Map<UUID, List<String>> tagsByItemId = itemCustomTagsService.getAllTagsByItemIds(itemIds)
        .stream()
        .collect(Collectors.toMap(ItemCustomTags::getItemId, ItemCustomTags::getCustomTags, (a, b) -> a));

    return new LookupBundle(imagesByItemId, tagsByItemId);
  }

  private record LookupBundle(
      Map<UUID, List<ItemImage>> images,
      Map<UUID, List<String>> tags
  ) {}
}
