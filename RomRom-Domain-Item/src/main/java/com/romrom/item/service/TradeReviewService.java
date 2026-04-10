package com.romrom.item.service;

import com.romrom.common.constant.TradeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.dto.TradeRequest;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.entity.postgres.TradeReview;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.item.repository.postgres.TradeReviewRepository;
import com.romrom.member.entity.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeReviewService {

  private final TradeReviewRepository tradeReviewRepository;
  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;

  // 후기 작성
  @Transactional
  public void postTradeReview(TradeRequest request) {
    TradeRequestHistory tradeRequestHistory = findTradeRequestHistoryById(request.getTradeRequestHistoryId());
    Member reviewerMember = request.getMember();

    // 종합 평가 필수 검증
    if (request.getTradeReviewRating() == null) {
      log.error("종합 평가가 누락되었습니다. memberId={}", reviewerMember.getMemberId());
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    // 거래 완료 상태 검증
    if (tradeRequestHistory.getTradeStatus() != TradeStatus.TRADED) {
      log.error("거래 완료 상태가 아닙니다. tradeRequestHistoryId={}, status={}",
          tradeRequestHistory.getTradeRequestHistoryId(), tradeRequestHistory.getTradeStatus());
      throw new CustomException(ErrorCode.TRADE_NOT_COMPLETED);
    }

    // 후기 작성 권한 검증 (거래 당사자만 작성 가능)
    Member takeItemOwner = tradeRequestHistory.getTakeItem().getMember();
    Member giveItemOwner = tradeRequestHistory.getGiveItem().getMember();
    boolean isTradeParticipant = reviewerMember.getMemberId().equals(takeItemOwner.getMemberId())
        || reviewerMember.getMemberId().equals(giveItemOwner.getMemberId());
    if (!isTradeParticipant) {
      log.error("거래 당사자가 아닙니다. memberId={}, tradeRequestHistoryId={}",
          reviewerMember.getMemberId(), tradeRequestHistory.getTradeRequestHistoryId());
      throw new CustomException(ErrorCode.TRADE_REVIEW_ACCESS_FORBIDDEN);
    }

    // 중복 후기 검증
    if (tradeReviewRepository.existsByTradeRequestHistoryAndReviewerMember(tradeRequestHistory, reviewerMember)) {
      log.error("이미 후기를 작성했습니다. memberId={}, tradeRequestHistoryId={}",
          reviewerMember.getMemberId(), tradeRequestHistory.getTradeRequestHistoryId());
      throw new CustomException(ErrorCode.TRADE_REVIEW_ALREADY_EXISTS);
    }

    // 후기 받는 사람 결정 (작성자의 반대편 거래 참여자)
    Member reviewedMember = reviewerMember.getMemberId().equals(takeItemOwner.getMemberId())
        ? giveItemOwner
        : takeItemOwner;

    TradeReview tradeReview = TradeReview.builder()
        .tradeRequestHistory(tradeRequestHistory)
        .reviewerMember(reviewerMember)
        .reviewedMember(reviewedMember)
        .tradeReviewRating(request.getTradeReviewRating())
        .tradeReviewTags(request.getTradeReviewTags())
        .reviewComment(request.getReviewComment())
        .build();
    try {
      tradeReviewRepository.save(tradeReview);
    } catch (DataIntegrityViolationException e) {
      log.warn("중복 후기 저장 시도 (DB 유니크 제약 위반): tradeRequestHistoryId={}, memberId={}",
          tradeRequestHistory.getTradeRequestHistoryId(), reviewerMember.getMemberId());
      throw new CustomException(ErrorCode.TRADE_REVIEW_ALREADY_EXISTS);
    }
    log.debug("후기 작성 완료: tradeReviewId={}", tradeReview.getTradeReviewId());
  }

  private TradeRequestHistory findTradeRequestHistoryById(UUID tradeRequestHistoryId) {
    return tradeRequestHistoryRepository.findByTradeRequestHistoryIdWithItems(tradeRequestHistoryId)
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));
  }
}
