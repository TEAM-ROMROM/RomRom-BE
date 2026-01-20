package com.romrom.item.service;

import com.romrom.common.constant.InteractionType;
import com.romrom.common.constant.ItemCategory;
import com.romrom.item.config.RecommendationConfig;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.UserInteractionScore;
import com.romrom.item.entity.postgres.ViewHistory;
import com.romrom.item.repository.postgres.UserInteractionScoreRepository;
import com.romrom.item.repository.postgres.ViewHistoryRepository;
import com.romrom.member.entity.Member;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserInteractionService {

  private final RecommendationConfig recommendationConfig;
  private final UserInteractionScoreRepository userInteractionScoreRepository;
  private final ViewHistoryRepository viewHistoryRepository;

  /**
   * 사용자의 물품 조회 이력 기록 (비동기 처리)
   */
  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordView(UUID memberId, UUID itemId, ItemCategory category) {
    log.debug("물품 조회: Member= {}, Item= {}", memberId, itemId);

    try {
      // 하루 1회 제한
      LocalDate today = LocalDate.now();
      boolean isAlreadyViewedToday = viewHistoryRepository.existsByMemberMemberIdAndItemItemIdAndViewedDate(
          memberId, itemId, today);

      if (isAlreadyViewedToday) {
        log.debug("오늘 이미 해당 물품을 조회했습니다: Member= {}, Item= {}", memberId, itemId);
        return;
      }

      ViewHistory history = ViewHistory.builder()
          .member(Member.builder().memberId(memberId).build())
          .item(Item.builder().itemId(itemId).build())
          .itemCategory(category)
          .viewedDate(today)
          .build();

      viewHistoryRepository.save(history);

      // 사용자 행동 점수 업데이트
      updateInteractionScore(memberId, category, InteractionType.VIEW);

    } catch (Exception e) {
      log.error("물품 조회 기록에 실패했습니다: {}", itemId, e);
    }
  }

  /**
   * 카테고리별 상호작용 점수 업데이트 (비동기 처리)
   * * @param InteractionType : VIEW(조회), LIKE(좋아요 등록), UNLIKE(좋아요 취소)
   */
  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void updateInteractionScore(UUID memberId, ItemCategory category, InteractionType type) {
    try {
      // 해당 유저의 카테고리별 점수 로우 조회 (없으면 생성)
      UserInteractionScore score = userInteractionScoreRepository.findByMemberMemberIdAndItemCategory(memberId, category)
          .orElseGet(() -> UserInteractionScore.builder()
              .member(Member.builder().memberId(memberId).build())
              .itemCategory(category)
              .build());

      // 타입에 따른 카운트 및 점수 가감 로직
      switch (type) {
        case VIEW -> score.setViewCount(score.getViewCount() + 1);
        case LIKE -> score.setLikeCount(score.getLikeCount() + 1);
        case UNLIKE -> score.setLikeCount(Math.max(0, score.getLikeCount() - 1)); // 음수 방지
      }

      // 공통 점수 계산 로직 실행
      score.updateScore(recommendationConfig.getWeight().getLike(), recommendationConfig.getWeight().getView());

      userInteractionScoreRepository.save(score);

      log.debug("카테고리 점수 업데이트 완료: Member={}, Category={}, Type={}, TotalScore={}",
          memberId, category, type, score.getTotalScore());

    } catch (Exception e) {
      log.error("점수 업데이트 중 오류 발생: Member={}, Category={}, Type={}", memberId, category, type, e);
    }
  }
}