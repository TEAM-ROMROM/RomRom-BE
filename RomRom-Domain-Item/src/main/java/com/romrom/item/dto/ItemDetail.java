package com.romrom.item.dto;

import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemTradeOption;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
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
public class ItemDetail {
  private UUID itemId;
  private UUID memberId;
  private String profileUrl;
  private String itemName;
  private String itemDescription;
  private ItemCategory itemCategory;
  private ItemCondition itemCondition;
  private List<ItemTradeOption> itemTradeOptions;
  private Integer likeCount;
  private Integer price;
  private LocalDateTime createdDate;
  private List<String> itemImageUrls;
  private List<String> itemCustomTags;
  private Double longitude;
  private Double latitude;

  public static ItemDetail from(Item item, List<ItemImage> itemImages, List<String> itemCustomTags) {
    return ItemDetail.builder()
        .itemId(item.getItemId())
        .memberId(item.getMember().getMemberId())
        .profileUrl(item.getMember().getProfileUrl())
        .itemName(item.getItemName())
        .itemDescription(item.getItemDescription())
        .itemCategory(item.getItemCategory())
        .itemCondition(item.getItemCondition())
        .itemTradeOptions(item.getItemTradeOptions())
        .likeCount(item.getLikeCount())
        .price(item.getPrice())
        .createdDate(item.getCreatedDate())
        .itemImageUrls(itemImages.stream()
            .map(ItemImage::getImageUrl)
            .collect(Collectors.toList()))
        .itemCustomTags(itemCustomTags)
        .longitude(item.getLocation().getX())
        .latitude(item.getLocation().getY())
        .build();
  }
}