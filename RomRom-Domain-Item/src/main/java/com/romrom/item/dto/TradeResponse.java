package com.romrom.item.dto;

import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.entity.postgres.TradeReview;
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
  private Boolean tradeRequestHistoryExists;

  // 후기 응답 필드
  private TradeReview tradeReview;
}
