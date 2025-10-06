package com.romrom.item.dto;

import com.romrom.item.entity.postgres.Item;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.domain.Page;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ItemResponse {

  private Item item;
  private Page<Item> itemPage;
  private Boolean isLiked;
  private Boolean isFirstItemPosted;
}
