package com.romrom.item.service;

import static org.mockito.Mockito.when;

import com.romrom.common.constant.TradeReviewRating;
import com.romrom.common.constant.TradeReviewTag;
import com.romrom.item.dto.TradeRequest;
import com.romrom.item.dto.TradeReviewStats;
import com.romrom.item.repository.postgres.TradeReviewRepository;
import com.romrom.item.repository.postgres.TradeReviewRepository.RatingCountProjection;
import com.romrom.item.repository.postgres.TradeReviewRepository.TagCountProjection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 받은 후기 통계 단위 테스트 (#858): 평점별·태그별 0 채움, 총 수, 평균 평점
 */
@ExtendWith(MockitoExtension.class)
class TradeReviewStatsTest {

  @Mock
  private TradeReviewRepository tradeReviewRepository;

  @InjectMocks
  private TradeReviewService tradeReviewService;

  private static RatingCountProjection ratingCountOf(TradeReviewRating tradeReviewRating, long count) {
    return new RatingCountProjection() {
      @Override
      public TradeReviewRating getTradeReviewRating() {
        return tradeReviewRating;
      }

      @Override
      public Long getCount() {
        return count;
      }
    };
  }

  private static TagCountProjection tagCountOf(TradeReviewTag tradeReviewTag, long count) {
    return new TagCountProjection() {
      @Override
      public TradeReviewTag getTradeReviewTag() {
        return tradeReviewTag;
      }

      @Override
      public Long getCount() {
        return count;
      }
    };
  }

  private TradeRequest createStatsRequest(UUID memberId) {
    TradeRequest statsRequest = new TradeRequest();
    statsRequest.setMemberId(memberId);
    return statsRequest;
  }

  @Test
  @DisplayName("GOOD 1 + GREAT 2 > 총 3, 평균 2.67, 없는 평점·태그는 0")
  void getReceivedTradeReviewStats_집계() {
    UUID memberId = UUID.randomUUID();
    when(tradeReviewRepository.countReceivedRatingsByMemberId(memberId))
        .thenReturn(List.of(ratingCountOf(TradeReviewRating.GOOD, 1), ratingCountOf(TradeReviewRating.GREAT, 2)));
    when(tradeReviewRepository.countReceivedTagsByMemberId(memberId))
        .thenReturn(List.of(tagCountOf(TradeReviewTag.KIND, 2)));

    TradeReviewStats stats = tradeReviewService.getReceivedTradeReviewStats(createStatsRequest(memberId))
        .getTradeReviewStats();

    Assertions.assertEquals(3L, stats.getTotalReviewCount());
    Assertions.assertEquals(2.67, stats.getAverageRatingScore());
    Assertions.assertEquals(0L, stats.getRatingCounts().get(TradeReviewRating.BAD));
    Assertions.assertEquals(2L, stats.getTagCounts().get(TradeReviewTag.KIND));
    Assertions.assertEquals(0L, stats.getTagCounts().get(TradeReviewTag.PUNCTUAL));
    Assertions.assertEquals(TradeReviewTag.values().length, stats.getTagCounts().size());
  }

  @Test
  @DisplayName("후기 없음 > 총 0, 평균 null")
  void getReceivedTradeReviewStats_후기없음() {
    UUID memberId = UUID.randomUUID();
    when(tradeReviewRepository.countReceivedRatingsByMemberId(memberId)).thenReturn(List.of());
    when(tradeReviewRepository.countReceivedTagsByMemberId(memberId)).thenReturn(List.of());

    TradeReviewStats stats = tradeReviewService.getReceivedTradeReviewStats(createStatsRequest(memberId))
        .getTradeReviewStats();

    Assertions.assertEquals(0L, stats.getTotalReviewCount());
    Assertions.assertNull(stats.getAverageRatingScore());
  }
}
