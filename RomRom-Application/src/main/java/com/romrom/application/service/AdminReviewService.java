package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.item.entity.postgres.TradeReview;
import com.romrom.item.repository.postgres.TradeReviewRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 후기 블라인드 관리 서비스
 * - 후기 목록(평점/기간/블라인드 필터), 블라인드/해제 처리 (Blindable 계약 기반)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReviewService {

  private static final DateTimeFormatter REVIEW_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final TradeReviewRepository tradeReviewRepository;

  /**
   * 관리자 후기 목록 조회 (평점/기간/블라인드여부 필터, 페이지네이션)
   */
  @Transactional(readOnly = true)
  public AdminResponse getReviewsForAdmin(AdminRequest request) {
    LocalDateTime startDateTime = parseStartDate(request.getStartDate());
    LocalDateTime endDateTime = parseEndDate(request.getEndDate());

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(request.getSortDirection(), request.getSortBy())
    );

    Page<TradeReview> reviewPage = tradeReviewRepository.findReviewsForAdmin(
        request.getTradeReviewRating(),
        startDateTime,
        endDateTime,
        request.getIsBlindedFilter(),
        pageable
    );

    log.info("관리자 후기 목록 조회: rating={}, isBlinded={}, totalElements={}",
        request.getTradeReviewRating(), request.getIsBlindedFilter(), reviewPage.getTotalElements());

    return AdminResponse.builder()
        .reviews(reviewPage)
        .totalCount(reviewPage.getTotalElements())
        .totalPages(reviewPage.getTotalPages())
        .totalElements(reviewPage.getTotalElements())
        .currentPage(reviewPage.getNumber())
        .build();
  }

  /**
   * 후기 블라인드 처리 (처리자/시각 기록)
   */
  @Transactional
  public AdminResponse blindReview(AdminRequest request) {
    TradeReview tradeReview = findReviewById(request.getTradeReviewId());
    UUID currentAdminId = getCurrentAdminId();

    tradeReview.blind(request.getBlindReason(), currentAdminId);

    // 사유 원문은 민감 정보가 포함될 수 있어 로그에 남기지 않는다 (ID 만 기록)
    log.info("관리자 후기 블라인드 처리: tradeReviewId={}, byAdminId={}",
        tradeReview.getTradeReviewId(), currentAdminId);

    return AdminResponse.builder().build();
  }

  /**
   * 후기 블라인드 해제 (처리 정보 초기화)
   */
  @Transactional
  public AdminResponse unblindReview(AdminRequest request) {
    TradeReview tradeReview = findReviewById(request.getTradeReviewId());

    tradeReview.unblind();

    log.info("관리자 후기 블라인드 해제: tradeReviewId={}", tradeReview.getTradeReviewId());

    return AdminResponse.builder().build();
  }

  private TradeReview findReviewById(UUID tradeReviewId) {
    return tradeReviewRepository.findById(tradeReviewId)
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REVIEW_NOT_FOUND));
  }

  /**
   * 현재 인증된 관리자(Member)의 memberId 추출
   * - admin 은 ROLE_ADMIN Member 이므로 SecurityContext 의 principal 에서 memberId 를 얻는다
   * - 인증 정보가 없으면 처리자 추적이 깨지므로 블라인드 처리를 진행하지 않고 인증 예외로 실패시킨다
   */
  private UUID getCurrentAdminId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails customUserDetails)) {
      log.error("관리자 인증 정보를 확인할 수 없어 블라인드 처리를 중단합니다.");
      throw new CustomException(ErrorCode.UNAUTHORIZED);
    }
    return customUserDetails.getMember().getMemberId();
  }

  private LocalDateTime parseStartDate(String startDateString) {
    LocalDate localDate = parseLocalDate(startDateString);
    return localDate == null ? null : localDate.atStartOfDay();
  }

  private LocalDateTime parseEndDate(String endDateString) {
    LocalDate localDate = parseLocalDate(endDateString);
    return localDate == null ? null : localDate.atTime(LocalTime.MAX);
  }

  /**
   * yyyy-MM-dd 파싱. 미입력은 null(전체 기간), 형식 오류는 INVALID_REQUEST 로 끊는다.
   */
  private LocalDate parseLocalDate(String dateString) {
    if (dateString == null || dateString.trim().isEmpty()) {
      return null;
    }
    try {
      return LocalDate.parse(dateString.trim(), REVIEW_DATE_FORMAT);
    } catch (Exception e) {
      log.warn("후기 관리 날짜 파싱 실패: {}", dateString);
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
  }
}
