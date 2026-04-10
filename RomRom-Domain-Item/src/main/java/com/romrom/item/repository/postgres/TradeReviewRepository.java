package com.romrom.item.repository.postgres;

import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.entity.postgres.TradeReview;
import com.romrom.member.entity.Member;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeReviewRepository extends JpaRepository<TradeReview, UUID> {

  // 동일 거래에 같은 작성자의 후기가 이미 존재하는지 확인
  boolean existsByTradeRequestHistoryAndReviewerMember(
      TradeRequestHistory tradeRequestHistory, Member reviewerMember);
}
