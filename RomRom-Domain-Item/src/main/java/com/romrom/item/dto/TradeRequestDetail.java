package com.romrom.item.dto;

import com.romrom.common.constant.ItemTradeOption;
import java.util.List;
import java.util.UUID;
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
public class TradeRequestDetail {

  private UUID tradeRequestHistoryId;
  private ItemDetail itemDetail;
  private List<ItemTradeOption> itemTradeOptions;
}
