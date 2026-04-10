package com.romrom.item.dto;

import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import lombok.*;
import org.springframework.data.domain.Page;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class TradeResponse {

  private TradeRequestHistory tradeRequestHistory;
  private Page<TradeRequestHistory> tradeRequestHistoryPage;
  private Page<Item> itemPage;
  private Boolean tradeRequestHistoryExists;
}
