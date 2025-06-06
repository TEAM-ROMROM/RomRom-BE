package com.romrom.romback.domain.object.dto;

import com.romrom.romback.domain.object.constant.ItemCategory;
import com.romrom.romback.domain.object.constant.ItemCondition;
import com.romrom.romback.domain.object.constant.ItemTradeOption;
import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.ItemImage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ItemDetailResponse {
  private UUID itemId;
  private UUID memberId;
  private String itemName;
  private String itemDescription;
  private ItemCategory itemCategory;
  private ItemCondition itemCondition;
  private List<ItemTradeOption> itemTradeOptions;
  private Integer likeCount;
  private Integer price;
  private LocalDateTime createdDate;
  private List<String> imageUrls;
  private List<String> itemCustomTags;

  public static ItemDetailResponse from(Item item, List<ItemImage> itemImages, List<String> itemCustomTags) {
    return ItemDetailResponse.builder()
        .itemId(item.getItemId())
        .memberId(item.getMember().getMemberId())
        .itemName(item.getItemName())
        .itemDescription(item.getItemDescription())
        .itemCategory(item.getItemCategory())
        .itemCondition(item.getItemCondition())
        .itemTradeOptions(item.getItemTradeOptions())
        .likeCount(item.getLikeCount())
        .price(item.getPrice())
        .createdDate(item.getCreatedDate())
        .imageUrls(itemImages.stream()
            .map(ItemImage::getImageUrl)
            .collect(Collectors.toList()))
        .itemCustomTags(itemCustomTags)
        .build();
  }
}