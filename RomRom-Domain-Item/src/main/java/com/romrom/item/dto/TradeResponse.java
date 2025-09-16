package com.romrom.item.dto;

import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import java.util.List;
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

  private List<ItemImage> itemImages;
  private Page<ItemDetail> itemDetailPage;
  private Page<TradeRequestHistory> tradeRequestHistoryPage;
  private TradeRequestHistory tradeRequestHistory;
}
