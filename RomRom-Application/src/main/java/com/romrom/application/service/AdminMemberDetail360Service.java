package com.romrom.application.service;

import com.romrom.ai.entity.mongo.AiUsageHistory;
import com.romrom.ai.repository.mongo.AiUsageHistoryRepository;
import com.romrom.application.dto.AdminMemberDetail360Dto;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.constant.AiUsageType;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.LoginResult;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.mongo.LikeHistory;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.mongo.LikeHistoryRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.entity.mongo.LoginHistory;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.member.repository.MemberRepository;
import com.romrom.member.repository.mongo.LoginHistoryRepository;
import com.romrom.member.repository.mongo.SanctionHistoryRepository;
import com.romrom.notification.entity.NotificationHistory;
import com.romrom.notification.repository.NotificationHistoryRepository;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.entity.MemberReport;
import com.romrom.report.repository.ItemReportRepository;
import com.romrom.report.repository.MemberReportRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 360 뷰: 13개 카드 병렬 조립
 * 카드별 비동기 조회(adminMemberDetailExecutor) > 실패 카드는 null
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMemberDetail360Service {

  private final MemberRepository memberRepository;
  private final ItemRepository itemRepository;
  private final MemberReportRepository memberReportRepository;
  private final ItemReportRepository itemReportRepository;
  private final SanctionHistoryRepository sanctionHistoryRepository;
  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final NotificationHistoryRepository notificationHistoryRepository;
  private final LikeHistoryRepository likeHistoryRepository;
  private final LoginHistoryRepository loginHistoryRepository;
  private final AiUsageHistoryRepository aiUsageHistoryRepository;
  private final MemberLocationRepository memberLocationRepository;

  // 필드명이 빈 이름(adminMemberDetailExecutor)과 일치하여 by-name 매칭됨
  private final Executor adminMemberDetailExecutor;

  /**
   * 회원 360 메인 메서드 — 12 카드 병렬 조회
   */
  @Transactional(readOnly = true)
  public AdminMemberDetail360Dto getMemberDetail360(UUID memberId) {
    if (!memberRepository.existsById(memberId)) {
      log.error("회원 360 조회 실패 - 회원 없음: memberId={}", memberId);
      throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
    }

    CompletableFuture<AdminMemberDetail360Dto.BasicInfoCard> basicInfoFuture =
        supplyCardAsync("basicInfo", () -> buildBasicInfoCard(memberId));
    CompletableFuture<AdminMemberDetail360Dto.OwnedItemsCard> ownedItemsFuture =
        supplyCardAsync("ownedItems", () -> buildOwnedItemsCard(memberId));
    CompletableFuture<AdminMemberDetail360Dto.TradesCard> tradesFuture =
        supplyCardAsync("trades", () -> buildTradesCard(memberId));
    CompletableFuture<AdminMemberDetail360Dto.ChatRoomsCard> chatRoomsFuture =
        supplyCardAsync("chatRooms", () -> buildChatRoomsCard(memberId));
    CompletableFuture<AdminMemberDetail360Dto.ReportsReceivedCard> reportsReceivedFuture =
        supplyCardAsync("reportsReceived", () -> buildReportsReceivedCard(memberId));
    CompletableFuture<AdminMemberDetail360Dto.ReportsFiledCard> reportsFiledFuture =
        supplyCardAsync("reportsFiled", () -> buildReportsFiledCard(memberId));
    CompletableFuture<AdminMemberDetail360Dto.SanctionsCard> sanctionsFuture =
        supplyCardAsync("sanctions", () -> buildSanctionsCard(memberId));
    CompletableFuture<AdminMemberDetail360Dto.NotificationConsentCard> notificationConsentFuture =
        supplyCardAsync("notificationConsent", () -> buildNotificationConsentCard(memberId));
    CompletableFuture<AdminMemberDetail360Dto.LoginHistoryCard> loginHistoryFuture =
        supplyCardAsync("loginHistory", () -> buildLoginHistoryCard(memberId));
    CompletableFuture<AdminMemberDetail360Dto.AppUsageCard> appUsageFuture =
        supplyCardAsync("appUsage", () -> buildAppUsageCard(memberId));
    CompletableFuture<AdminMemberDetail360Dto.LikesCard> likesFuture =
        supplyCardAsync("likes", () -> buildLikesCard(memberId));
    CompletableFuture<AdminMemberDetail360Dto.AiUsageCard> aiUsageFuture =
        supplyCardAsync("aiUsage", () -> buildAiUsageCard(memberId));
    CompletableFuture<AdminMemberDetail360Dto.TradeStatsCard> tradeStatsFuture =
        supplyCardAsync("tradeStats", () -> buildTradeStatsCard(memberId));

    CompletableFuture.allOf(
        basicInfoFuture, ownedItemsFuture, tradesFuture, chatRoomsFuture,
        reportsReceivedFuture, reportsFiledFuture, sanctionsFuture, notificationConsentFuture,
        loginHistoryFuture, appUsageFuture, likesFuture, aiUsageFuture, tradeStatsFuture
    ).join();

    return AdminMemberDetail360Dto.builder()
        .basicInfoCard(basicInfoFuture.join())
        .ownedItemsCard(ownedItemsFuture.join())
        .tradesCard(tradesFuture.join())
        .chatRoomsCard(chatRoomsFuture.join())
        .reportsReceivedCard(reportsReceivedFuture.join())
        .reportsFiledCard(reportsFiledFuture.join())
        .sanctionsCard(sanctionsFuture.join())
        .notificationConsentCard(notificationConsentFuture.join())
        .loginHistoryCard(loginHistoryFuture.join())
        .appUsageCard(appUsageFuture.join())
        .likesCard(likesFuture.join())
        .aiUsageCard(aiUsageFuture.join())
        .tradeStatsCard(tradeStatsFuture.join())
        .build();
  }

  private <T> CompletableFuture<T> supplyCardAsync(String cardName, java.util.function.Supplier<T> cardSupplier) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return cardSupplier.get();
      } catch (Exception cardBuildException) {
        log.warn("360 카드 빌드 실패 - 카드={}, 메시지={}", cardName, cardBuildException.getMessage(), cardBuildException);
        return null;
      }
    }, adminMemberDetailExecutor);
  }

  private AdminMemberDetail360Dto.BasicInfoCard buildBasicInfoCard(UUID memberId) {
    Member targetMember = memberRepository.findById(memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    MemberLocation memberLocation = memberLocationRepository.findByMemberMemberId(memberId).orElse(null);
    SanctionHistory activeSanction = sanctionHistoryRepository
        .findFirstByMemberIdAndLiftedAtIsNullOrderBySuspendedAtDesc(memberId).orElse(null);
    return AdminMemberDetail360Dto.BasicInfoCard.builder()
        .member(targetMember)
        .memberLocation(memberLocation)
        .activeSanction(activeSanction)
        .build();
  }

  private AdminMemberDetail360Dto.OwnedItemsCard buildOwnedItemsCard(UUID memberId) {
    Pageable recentItemsPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdDate"));
    Page<Item> recentItemsPage = itemRepository.findByMemberMemberIdOrderByCreatedDateDesc(memberId, recentItemsPageable);

    long totalItemCount = itemRepository.countByMemberMemberId(memberId);
    long deletedItemCount = itemRepository.countByMemberMemberIdAndIsDeletedTrue(memberId);
    long forSaleCount = itemRepository.countByMemberMemberIdAndItemStatusAndIsDeletedFalse(memberId, ItemStatus.AVAILABLE);
    long soldOutCount = itemRepository.countByMemberMemberIdAndItemStatusAndIsDeletedFalse(memberId, ItemStatus.EXCHANGED);
    // ItemStatus에 RESERVED 없음 - 0 고정
    long reservedCount = 0L;

    return AdminMemberDetail360Dto.OwnedItemsCard.builder()
        .totalCount(totalItemCount)
        .forSaleCount(forSaleCount)
        .reservedCount(reservedCount)
        .soldOutCount(soldOutCount)
        .deletedCount(deletedItemCount)
        .recentItems(recentItemsPage.getContent())
        .build();
  }

  private AdminMemberDetail360Dto.TradesCard buildTradesCard(UUID memberId) {
    Pageable recentTradesPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdDate"));
    Page<TradeRequestHistory> recentTradesPage = tradeRequestHistoryRepository.findByMemberIdEitherSide(memberId, recentTradesPageable);

    long totalTradeCount = tradeRequestHistoryRepository.countByMemberIdEitherSide(memberId);
    long requestedByMemberCount = tradeRequestHistoryRepository.countByGiveItemMemberMemberId(memberId);
    long receivedByMemberCount = tradeRequestHistoryRepository.countByTakeItemMemberMemberId(memberId);
    long pendingCount = tradeRequestHistoryRepository.countByMemberIdEitherSideAndTradeStatus(memberId, TradeStatus.PENDING);
    long chattingCount = tradeRequestHistoryRepository.countByMemberIdEitherSideAndTradeStatus(memberId, TradeStatus.CHATTING);
    long tradedCount = tradeRequestHistoryRepository.countByMemberIdEitherSideAndTradeStatus(memberId, TradeStatus.TRADED);
    long canceledCount = tradeRequestHistoryRepository.countByMemberIdEitherSideAndTradeStatus(memberId, TradeStatus.CANCELED);

    return AdminMemberDetail360Dto.TradesCard.builder()
        .totalCount(totalTradeCount)
        .requestedByMemberCount(requestedByMemberCount)
        .receivedByMemberCount(receivedByMemberCount)
        .pendingCount(pendingCount)
        .chattingCount(chattingCount)
        .tradedCount(tradedCount)
        .canceledCount(canceledCount)
        .recentTrades(recentTradesPage.getContent())
        .build();
  }

  private AdminMemberDetail360Dto.ChatRoomsCard buildChatRoomsCard(UUID memberId) {
    Pageable recentChatRoomsPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdDate"));
    Page<ChatRoom> recentChatRoomsPage = chatRoomRepository.findByMemberIdEitherSide(memberId, recentChatRoomsPageable);
    long totalChatRoomCount = chatRoomRepository.countByMemberIdEitherSide(memberId);
    long activeChatRoomCount = chatRoomRepository.countActiveByMemberIdEitherSide(memberId);

    return AdminMemberDetail360Dto.ChatRoomsCard.builder()
        .totalCount(totalChatRoomCount)
        .activeCount(activeChatRoomCount)
        .recentChatRooms(recentChatRoomsPage.getContent())
        .build();
  }

  private AdminMemberDetail360Dto.ReportsReceivedCard buildReportsReceivedCard(UUID memberId) {
    Member targetMember = memberRepository.findById(memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    Pageable recentReportsPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdDate"));

    Page<ItemReport> recentItemReportsPage = itemReportRepository.findByItemMemberOrderByCreatedDateDesc(targetMember, recentReportsPageable);
    Page<MemberReport> recentMemberReportsPage = memberReportRepository.findByTargetMemberOrderByCreatedDateDesc(targetMember, recentReportsPageable);

    long itemReportReceivedCount = itemReportRepository.countByItemMember(targetMember);
    long memberReportReceivedCount = memberReportRepository.countByTargetMember(targetMember);

    return AdminMemberDetail360Dto.ReportsReceivedCard.builder()
        .totalCount(itemReportReceivedCount + memberReportReceivedCount)
        .itemReportCount(itemReportReceivedCount)
        .memberReportCount(memberReportReceivedCount)
        .recentItemReports(recentItemReportsPage.getContent())
        .recentMemberReports(recentMemberReportsPage.getContent())
        .build();
  }

  private AdminMemberDetail360Dto.ReportsFiledCard buildReportsFiledCard(UUID memberId) {
    Member reporterMember = memberRepository.findById(memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    Pageable recentReportsPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdDate"));

    Page<ItemReport> recentItemReportsFiledPage = itemReportRepository.findByMemberOrderByCreatedDateDesc(reporterMember, recentReportsPageable);
    Page<MemberReport> recentMemberReportsFiledPage = memberReportRepository.findByReporterOrderByCreatedDateDesc(reporterMember, recentReportsPageable);

    long itemReportFiledCount = itemReportRepository.countByMember(reporterMember);
    long memberReportFiledCount = memberReportRepository.countByReporter(reporterMember);

    return AdminMemberDetail360Dto.ReportsFiledCard.builder()
        .totalCount(itemReportFiledCount + memberReportFiledCount)
        .itemReportCount(itemReportFiledCount)
        .memberReportCount(memberReportFiledCount)
        .recentItemReportsFiled(recentItemReportsFiledPage.getContent())
        .recentMemberReportsFiled(recentMemberReportsFiledPage.getContent())
        .build();
  }

  private AdminMemberDetail360Dto.SanctionsCard buildSanctionsCard(UUID memberId) {
    Pageable recentSanctionsPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "suspendedAt"));
    Page<SanctionHistory> recentSanctionsPage = sanctionHistoryRepository.findByMemberId(memberId, recentSanctionsPageable);
    Optional<SanctionHistory> activeSanctionOptional = sanctionHistoryRepository
        .findFirstByMemberIdAndLiftedAtIsNullOrderBySuspendedAtDesc(memberId);

    return AdminMemberDetail360Dto.SanctionsCard.builder()
        .totalCount(recentSanctionsPage.getTotalElements())
        .isCurrentlyActive(activeSanctionOptional.isPresent())
        .activeSanction(activeSanctionOptional.orElse(null))
        .recentSanctions(recentSanctionsPage.getContent())
        .build();
  }

  private AdminMemberDetail360Dto.NotificationConsentCard buildNotificationConsentCard(UUID memberId) {
    Member consentMember = memberRepository.findById(memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    Pageable recentNotificationsPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "publishedAt"));
    Page<NotificationHistory> recentNotificationsPage = notificationHistoryRepository
        .findByMember_MemberIdOrderByPublishedAtDesc(memberId, recentNotificationsPageable);
    long totalNotificationCount = notificationHistoryRepository.countByMember_MemberId(memberId);

    return AdminMemberDetail360Dto.NotificationConsentCard.builder()
        .isMarketingInfoAgreed(consentMember.getIsMarketingInfoAgreed())
        .isActivityNotificationAgreed(consentMember.getIsActivityNotificationAgreed())
        .isChatNotificationAgreed(consentMember.getIsChatNotificationAgreed())
        .isContentNotificationAgreed(consentMember.getIsContentNotificationAgreed())
        .isTradeNotificationAgreed(consentMember.getIsTradeNotificationAgreed())
        .totalCount(totalNotificationCount)
        .recentNotifications(recentNotificationsPage.getContent())
        .build();
  }

  private AdminMemberDetail360Dto.LoginHistoryCard buildLoginHistoryCard(UUID memberId) {
    Pageable recentLoginsPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "loginAt"));
    Page<LoginHistory> recentLoginsPage = loginHistoryRepository.findByMemberIdOrderByLoginAtDesc(memberId, recentLoginsPageable);
    long totalLoginCount = loginHistoryRepository.countByMemberId(memberId);
    long successLoginCount = loginHistoryRepository.countByMemberIdAndLoginResult(memberId, LoginResult.SUCCESS);
    long failLoginCount = loginHistoryRepository.countByMemberIdAndLoginResult(memberId, LoginResult.FAIL);

    Optional<LoginHistory> lastSuccessfulLoginOptional = loginHistoryRepository
        .findFirstByMemberIdAndLoginResultOrderByLoginAtDesc(memberId, LoginResult.SUCCESS);
    LocalDateTime lastLoginAt = lastSuccessfulLoginOptional.map(LoginHistory::getLoginAt).orElse(null);
    String lastIpAddress = lastSuccessfulLoginOptional.map(LoginHistory::getIpAddress).orElse(null);

    return AdminMemberDetail360Dto.LoginHistoryCard.builder()
        .totalCount(totalLoginCount)
        .successCount(successLoginCount)
        .failCount(failLoginCount)
        .lastLoginAt(lastLoginAt)
        .lastIpAddress(lastIpAddress)
        .recentLogins(recentLoginsPage.getContent())
        .build();
  }

  private AdminMemberDetail360Dto.AppUsageCard buildAppUsageCard(UUID memberId) {
    Member appUsageMember = memberRepository.findById(memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    long daysSinceJoin = 0L;
    if (appUsageMember.getCreatedDate() != null) {
      daysSinceJoin = ChronoUnit.DAYS.between(appUsageMember.getCreatedDate(), LocalDateTime.now());
    }
    return AdminMemberDetail360Dto.AppUsageCard.builder()
        .lastActiveAt(appUsageMember.getLastActiveAt())
        .daysSinceJoin(daysSinceJoin)
        .build();
  }

  private AdminMemberDetail360Dto.LikesCard buildLikesCard(UUID memberId) {
    Pageable recentLikesPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdDate"));
    Page<LikeHistory> recentLikesPage = likeHistoryRepository.findByMemberId(memberId, recentLikesPageable);
    long totalLikeCount = likeHistoryRepository.countByMemberId(memberId);
    return AdminMemberDetail360Dto.LikesCard.builder()
        .totalCount(totalLikeCount)
        .recentLikes(recentLikesPage.getContent())
        .build();
  }

  private AdminMemberDetail360Dto.AiUsageCard buildAiUsageCard(UUID memberId) {
    Pageable recentAiUsagesPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "requestedAt"));
    Page<AiUsageHistory> recentAiUsagesPage = aiUsageHistoryRepository.findByMemberIdOrderByRequestedAtDesc(memberId, recentAiUsagesPageable);

    long totalAiUsageCount = aiUsageHistoryRepository.countByMemberId(memberId);
    long pricePredictionCount = aiUsageHistoryRepository.countByMemberIdAndAiUsageType(memberId, AiUsageType.PRICE_PREDICTION);
    long ugcFilterCount = aiUsageHistoryRepository.countByMemberIdAndAiUsageType(memberId, AiUsageType.UGC_FILTER);
    long imageAnalysisCount = aiUsageHistoryRepository.countByMemberIdAndAiUsageType(memberId, AiUsageType.IMAGE_ANALYSIS);
    long embeddingCount = aiUsageHistoryRepository.countByMemberIdAndAiUsageType(memberId, AiUsageType.EMBEDDING);
    long categoryMatchingCount = aiUsageHistoryRepository.countByMemberIdAndAiUsageType(memberId, AiUsageType.CATEGORY_MATCHING);

    return AdminMemberDetail360Dto.AiUsageCard.builder()
        .totalCount(totalAiUsageCount)
        .pricePredictionCount(pricePredictionCount)
        .ugcFilterCount(ugcFilterCount)
        .imageAnalysisCount(imageAnalysisCount)
        .embeddingCount(embeddingCount)
        .categoryMatchingCount(categoryMatchingCount)
        .recentUsages(recentAiUsagesPage.getContent())
        .build();
  }

  private AdminMemberDetail360Dto.TradeStatsCard buildTradeStatsCard(UUID memberId) {
    long totalTradeCount = tradeRequestHistoryRepository.countByMemberIdEitherSide(memberId);
    long tradedCount = tradeRequestHistoryRepository.countByMemberIdEitherSideAndTradeStatus(memberId, TradeStatus.TRADED);
    long canceledCount = tradeRequestHistoryRepository.countByMemberIdEitherSideAndTradeStatus(memberId, TradeStatus.CANCELED);

    double completionRate = totalTradeCount == 0 ? 0.0 : (double) tradedCount / totalTradeCount * 100.0;
    double cancellationRate = totalTradeCount == 0 ? 0.0 : (double) canceledCount / totalTradeCount * 100.0;

    // avgTradeDays / longestPendingDays — 별도 통계 쿼리 필요. 현재는 null/0 (후속 작업으로 미룸)
    return AdminMemberDetail360Dto.TradeStatsCard.builder()
        .completionRate(completionRate)
        .cancellationRate(cancellationRate)
        .avgTradeDays(null)
        .longestPendingDays(0L)
        .build();
  }
}
