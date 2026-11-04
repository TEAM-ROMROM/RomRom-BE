package com.romrom.item.dto;

import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.entity.postgres.TradeReview;
import java.util.List;
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
  private Page<TradeReview> tradeReviewPage;

  // 거래 요청 상세: 내가 이 거래 후기를 썼는지 (미조회 > null, #858)
  private Boolean isReviewWrittenByMe;

  // 받은 후기 통계 (/api/trade/review/stats, #858)
  private TradeReviewStats tradeReviewStats;

  // 탈퇴 전 안내용 진행 중 거래 목록 (#862)
  private List<TradeRequestHistory> ongoingTradeRequestHistories;
}
