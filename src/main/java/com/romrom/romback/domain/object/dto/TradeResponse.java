package com.romrom.romback.domain.object.dto;

import com.romrom.romback.domain.object.constant.ItemTradeOption;
import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.ItemImage;
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
public class TradeResponse {

  private Item item;
  private List<ItemImage> itemImages;
  private List<ItemTradeOption> itemTradeOptions;
}
