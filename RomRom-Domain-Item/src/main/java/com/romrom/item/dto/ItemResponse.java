package com.romrom.item.dto;

import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import com.romrom.common.constant.LikeStatus;
import org.springframework.data.domain.Page;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ItemResponse {

  private Item item;
  private List<ItemImage> itemImages;
  private List<String> itemCustomTags;
  private LikeStatus likeStatus;
  private Integer likeCount;
  private Page<ItemDetail> itemDetailPage;
}
