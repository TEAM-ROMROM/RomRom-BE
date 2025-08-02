package com.romrom.item.dto;

import com.romrom.item.entity.postgres.Item;
import java.util.List;
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
public class ItemImageResponse {

  private Item item;
  private List<String> itemImageUrls;
}
