package com.romrom.item.dto;

import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
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
public class TradeResponse {

  private TradeRequestHistory tradeRequestHistory;
  private Page<TradeRequestHistory> tradeRequestHistoryPage;
  private Page<Item> itemPage;
  private boolean tradeRequestHistoryExists;
}
