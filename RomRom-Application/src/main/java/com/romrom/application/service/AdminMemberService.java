package com.romrom.application.service;

import com.romrom.ai.entity.mongo.AiUsageHistory;
import com.romrom.ai.repository.mongo.AiUsageHistoryRepository;
import com.romrom.application.dto.AdminMemberDetail360Dto;
import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.dto.BulkActionResult;
import static com.romrom.auth.jwt.JwtUtil.REFRESH_KEY_PREFIX;

import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.AiUsageType;
import com.romrom.common.constant.ItemAdminDeleteReason;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.LoginResult;
import com.romrom.common.constant.Role;
import com.romrom.common.constant.SanctionType;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.mongo.LikeHistory;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.mongo.LikeHistoryRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.item.service.ItemService;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.entity.mongo.LoginHistory;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.member.repository.MemberRepository;
import com.romrom.member.repository.mongo.LoginHistoryRepository;
import com.romrom.member.repository.mongo.SanctionHistoryRepository;
import com.romrom.member.service.MemberService;
import com.romrom.notification.entity.NotificationHistory;
import com.romrom.notification.event.NotificationType;
import com.romrom.notification.repository.NotificationHistoryRepository;
import com.romrom.notification.service.NotificationService;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.entity.MemberReport;
import com.romrom.report.enums.ReportStatus;
import com.romrom.report.enums.ReportType;
import com.romrom.report.repository.ItemReportRepository;
import com.romrom.report.repository.MemberReportRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMemberService {

  private final MemberRepository memberRepository;
  private final ItemRepository itemRepository;
  private final MemberReportRepository memberReportRepository;
  private final ItemReportRepository itemReportRepository;
  private final SanctionHistoryRepository sanctionHistoryRepository;
  private final RedisTemplate<String, Object> redisTemplate;

  // === 360 View / Action 추가 의존성 ===
  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final NotificationHistoryRepository notificationHistoryRepository;
  private final LikeHistoryRepository likeHistoryRepository;
  private final LoginHistoryRepository loginHistoryRepository;
  private final AiUsageHistoryRepository aiUsageHistoryRepository;
  private final MemberLocationRepository memberLocationRepository;
  private final ItemService itemService;
  private final MemberService memberService;
  private final NotificationService notificationService;

  // 필드명이 빈 이름(adminMemberDetailExecutor)과 일치하여 by-name 매칭됨
  private final Executor adminMemberDetailExecutor;

  // ==================== 기존 메서드 ====================

  /**
   * 관리자용 회원 목록 조회 (페이지네이션, 검색 지원)
   */
  @Transactional(readOnly = true)
  public AdminResponse getMembersForAdmin(AdminRequest request) {
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(request.getSortDirection(), request.getSortBy())
    );

    Page<Member> memberPage;
    boolean hasKeyword = StringUtils.hasText(request.getSearchKeyword());
    boolean hasAccountStatusFilter = request.getAccountStatus() != null;
    boolean hasSuspendTypeFilter = StringUtils.hasText(request.getSuspendType());
    String trimmedKeyword = hasKeyword ? request.getSearchKeyword().trim() : null;

    if (hasSuspendTypeFilter && hasAccountStatusFilter) {
      boolean isPermanentFilter = "permanent".equals(request.getSuspendType());
      if (hasKeyword) {
        memberPage = isPermanentFilter
            ? memberRepository.searchPermanentSuspendedMembers(trimmedKeyword, request.getAccountStatus(), AccountStatus.PERMANENT_SUSPENSION_UNTIL, pageable)
            : memberRepository.searchTemporarySuspendedMembers(trimmedKeyword, request.getAccountStatus(), AccountStatus.PERMANENT_SUSPENSION_UNTIL, pageable);
      } else {
        memberPage = isPermanentFilter
            ? memberRepository.findPermanentSuspendedMembers(request.getAccountStatus(), AccountStatus.PERMANENT_SUSPENSION_UNTIL, pageable)
            : memberRepository.findTemporarySuspendedMembers(request.getAccountStatus(), AccountStatus.PERMANENT_SUSPENSION_UNTIL, pageable);
      }
    } else if (hasKeyword && hasAccountStatusFilter) {
      memberPage = memberRepository.searchByKeywordAndAccountStatusAndIsDeletedFalse(
          trimmedKeyword, request.getAccountStatus(), pageable);
    } else if (hasKeyword) {
      memberPage = memberRepository.searchByKeywordAndIsDeletedFalse(trimmedKeyword, pageable);
    } else if (hasAccountStatusFilter) {
      memberPage = memberRepository.findByAccountStatusAndIsDeletedFalse(request.getAccountStatus(), pageable);
    } else {
      memberPage = memberRepository.findByIsDeletedFalse(pageable);
    }

    return AdminResponse.builder()
        .members(memberPage)
        .totalCount(memberPage.getTotalElements())
        .build();
  }

  @Transactional(readOnly = true)
  public AdminResponse getRecentMembersForAdmin(int limit) {
    Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdDate"));
    Page<Member> memberPage = memberRepository.findByIsDeletedFalse(pageable);
    return AdminResponse.builder()
        .members(memberPage)
        .totalCount(memberPage.getTotalElements())
        .build();
  }

  @Transactional(readOnly = true)
  public AdminResponse getMemberDetailForAdmin(AdminRequest request) {
    Member member = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    List<Item> items = itemRepository.findByMemberAndIsDeletedFalseOrderByCreatedDateDesc(member);
    List<MemberReport> memberReports = memberReportRepository.findByTargetMemberOrderByCreatedDateDesc(member);

    AdminMemberDetail360Dto memberDetail360Dto = getMemberDetail360(member.getMemberId());

    return AdminResponse.builder()
        .memberDetail(AdminResponse.AdminMemberDetailDto.builder()
            .member(member)
            .items(items)
            .memberReports(memberReports)
            .reportCount((long) memberReports.size())
            .build())
        .memberDetail360(memberDetail360Dto)
        .build();
  }

  @Transactional
  public AdminResponse updateMemberStatusForAdmin(AdminRequest request) {
    Member member = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    if (request.getAccountStatus() == AccountStatus.SUSPENDED_ACCOUNT) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    member.setAccountStatus(request.getAccountStatus());
    memberRepository.save(member);

    return AdminResponse.builder().member(member).build();
  }

  @Transactional
  public AdminResponse suspendMember(AdminRequest request) {
    log.debug("회원 정지 처리: memberId={}, suspendReason={}", request.getMemberId(), request.getSuspendReason());

    // reportId 제공 시 reportType 필수 — 사전 검증 (이후 Redis/DB 변경 전에 실패해야 함)
    if (request.getReportId() != null && request.getReportType() == null) {
      log.warn("신고 처리 요청에 reportType 누락: reportId={}", request.getReportId());
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    Member targetMember = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    targetMember.setAccountStatus(AccountStatus.SUSPENDED_ACCOUNT);
    targetMember.setSuspendReason(request.getSuspendReason());
    targetMember.setSuspendedAt(LocalDateTime.now(ZoneOffset.UTC));

    if (request.getSuspendedUntil() != null && !request.getSuspendedUntil().isBlank()) {
      try {
        DateTimeFormatter suspendDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        targetMember.setSuspendedUntil(LocalDateTime.parse(request.getSuspendedUntil(), suspendDateTimeFormatter));
      } catch (DateTimeParseException parseException) {
        throw new CustomException(ErrorCode.INVALID_REQUEST);
      }
    } else {
      targetMember.setSuspendedUntil(AccountStatus.PERMANENT_SUSPENSION_UNTIL);
    }

    memberRepository.save(targetMember);

    Optional<SanctionHistory> activeSanctionHistory = sanctionHistoryRepository
        .findFirstByMemberIdAndLiftedAtIsNullOrderBySuspendedAtDesc(targetMember.getMemberId());
    if (activeSanctionHistory.isPresent()) {
      SanctionHistory previousSanction = activeSanctionHistory.get();
      previousSanction.setLiftedAt(LocalDateTime.now(ZoneOffset.UTC));
      previousSanction.setLiftedReason("제재 변경");
      sanctionHistoryRepository.save(previousSanction);
    }

    SanctionHistory newSanctionHistory = SanctionHistory.builder()
        .memberId(targetMember.getMemberId())
        .suspendReason(targetMember.getSuspendReason())
        .suspendedAt(targetMember.getSuspendedAt())
        .suspendedUntil(targetMember.getSuspendedUntil())
        .reportId(request.getReportId())
        .reportType(request.getReportType() != null ? request.getReportType().name() : null)
        .sanctionType(SanctionType.SUSPEND)
        .build();
    sanctionHistoryRepository.save(newSanctionHistory);

    String refreshTokenRedisKey = REFRESH_KEY_PREFIX + targetMember.getMemberId();
    redisTemplate.delete(refreshTokenRedisKey);

    if (request.getReportId() != null) {
      updateReportStatusToCompleted(request);
    }

    return AdminResponse.builder().member(targetMember).build();
  }

  @Transactional
  public AdminResponse unsuspendMember(AdminRequest request) {
    Member targetMember = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    if (targetMember.getAccountStatus() != AccountStatus.SUSPENDED_ACCOUNT) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    Optional<SanctionHistory> activeSanctionHistory = sanctionHistoryRepository
        .findFirstByMemberIdAndLiftedAtIsNullOrderBySuspendedAtDesc(targetMember.getMemberId());
    if (activeSanctionHistory.isPresent()) {
      SanctionHistory sanctionToLift = activeSanctionHistory.get();
      sanctionToLift.setLiftedAt(LocalDateTime.now(ZoneOffset.UTC));
      sanctionToLift.setLiftedReason("수동 해제");
      sanctionHistoryRepository.save(sanctionToLift);
    }

    targetMember.setAccountStatus(AccountStatus.ACTIVE_ACCOUNT);
    targetMember.setSuspendReason(null);
    targetMember.setSuspendedAt(null);
    targetMember.setSuspendedUntil(null);
    memberRepository.save(targetMember);

    return AdminResponse.builder().member(targetMember).build();
  }

  @Transactional(readOnly = true)
  public AdminResponse getMemberSanctionHistory(AdminRequest request) {
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "suspendedAt")
    );

    Page<SanctionHistory> sanctionHistoryPage = sanctionHistoryRepository
        .findByMemberId(request.getMemberId(), pageable);

    return AdminResponse.builder()
        .sanctionHistories(sanctionHistoryPage)
        .totalPages(sanctionHistoryPage.getTotalPages())
        .totalElements(sanctionHistoryPage.getTotalElements())
        .currentPage(sanctionHistoryPage.getNumber())
        .build();
  }

  @Transactional(readOnly = true)
  public AdminResponse getAllSanctionHistory(AdminRequest request) {
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "suspendedAt")
    );

    Page<SanctionHistory> sanctionHistoryPage = sanctionHistoryRepository.findAll(pageable);

    return AdminResponse.builder()
        .sanctionHistories(sanctionHistoryPage)
        .totalPages(sanctionHistoryPage.getTotalPages())
        .totalElements(sanctionHistoryPage.getTotalElements())
        .currentPage(sanctionHistoryPage.getNumber())
        .build();
  }

  private void updateReportStatusToCompleted(AdminRequest request) {
    if (request.getReportType() == ReportType.ITEM) {
      ItemReport itemReport = itemReportRepository.findById(request.getReportId())
          .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));
      itemReport.setStatus(ReportStatus.COMPLETED);
      itemReportRepository.save(itemReport);
    } else if (request.getReportType() == ReportType.MEMBER) {
      MemberReport memberReport = memberReportRepository.findById(request.getReportId())
          .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));
      memberReport.setStatus(ReportStatus.COMPLETED);
      memberReportRepository.save(memberReport);
    }
  }

  // ==================== 회원 360 View ====================

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

  // ---------- 카드 빌더 ----------

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

  // ==================== Sub-list 메서드 ====================

  @Transactional(readOnly = true)
  public Page<Item> listOwnedItems(UUID memberId, Pageable pageable) {
    return itemRepository.findByMemberMemberIdOrderByCreatedDateDesc(memberId, pageable);
  }

  @Transactional(readOnly = true)
  public Page<TradeRequestHistory> listTrades(UUID memberId, String tradeSide, Pageable pageable) {
    if ("GIVE".equalsIgnoreCase(tradeSide)) {
      return tradeRequestHistoryRepository.findByGiveItemMemberId(memberId, pageable);
    }
    if ("TAKE".equalsIgnoreCase(tradeSide)) {
      return tradeRequestHistoryRepository.findByTakeItemMemberId(memberId, pageable);
    }
    return tradeRequestHistoryRepository.findByMemberIdEitherSide(memberId, pageable);
  }

  @Transactional(readOnly = true)
  public Page<ChatRoom> listChatRooms(UUID memberId, Pageable pageable) {
    return chatRoomRepository.findByMemberIdEitherSide(memberId, pageable);
  }

  @Transactional(readOnly = true)
  public Page<ItemReport> listItemReportsReceived(UUID memberId, Pageable pageable) {
    Member targetMember = memberRepository.findById(memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    return itemReportRepository.findByItemMemberOrderByCreatedDateDesc(targetMember, pageable);
  }

  @Transactional(readOnly = true)
  public Page<MemberReport> listMemberReportsReceived(UUID memberId, Pageable pageable) {
    Member targetMember = memberRepository.findById(memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    return memberReportRepository.findByTargetMemberOrderByCreatedDateDesc(targetMember, pageable);
  }

  @Transactional(readOnly = true)
  public Page<ItemReport> listItemReportsFiled(UUID memberId, Pageable pageable) {
    Member reporterMember = memberRepository.findById(memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    return itemReportRepository.findByMemberOrderByCreatedDateDesc(reporterMember, pageable);
  }

  @Transactional(readOnly = true)
  public Page<MemberReport> listMemberReportsFiled(UUID memberId, Pageable pageable) {
    Member reporterMember = memberRepository.findById(memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    return memberReportRepository.findByReporterOrderByCreatedDateDesc(reporterMember, pageable);
  }

  @Transactional(readOnly = true)
  public Page<SanctionHistory> listSanctions(UUID memberId, Pageable pageable) {
    return sanctionHistoryRepository.findByMemberId(memberId, pageable);
  }

  @Transactional(readOnly = true)
  public Page<LoginHistory> listLoginHistory(UUID memberId, LoginResult loginResult, Pageable pageable) {
    // 전체 페이지를 SUCCESS/FAIL로 필터링하는 메서드가 별도로 필요하므로
    // loginResult가 지정되었으면 전체 가져와서 필터, 아니면 전체 페이지 반환
    Page<LoginHistory> loginHistoryPage = loginHistoryRepository.findByMemberIdOrderByLoginAtDesc(memberId, pageable);
    if (loginResult == null) {
      return loginHistoryPage;
    }
    List<LoginHistory> filteredLoginHistories = new ArrayList<>();
    for (LoginHistory loginHistoryEntity : loginHistoryPage.getContent()) {
      if (loginResult.equals(loginHistoryEntity.getLoginResult())) {
        filteredLoginHistories.add(loginHistoryEntity);
      }
    }
    return new PageImpl<>(filteredLoginHistories, pageable, loginHistoryPage.getTotalElements());
  }

  @Transactional(readOnly = true)
  public Page<LikeHistory> listLikes(UUID memberId, Pageable pageable) {
    return likeHistoryRepository.findByMemberId(memberId, pageable);
  }

  @Transactional(readOnly = true)
  public Page<AiUsageHistory> listAiUsage(UUID memberId, AiUsageType aiUsageType, Pageable pageable) {
    if (aiUsageType == null) {
      return aiUsageHistoryRepository.findByMemberIdOrderByRequestedAtDesc(memberId, pageable);
    }
    return aiUsageHistoryRepository.findByMemberIdAndAiUsageTypeOrderByRequestedAtDesc(memberId, aiUsageType, pageable);
  }

  @Transactional(readOnly = true)
  public Page<NotificationHistory> listNotifications(UUID memberId, Pageable pageable) {
    return notificationHistoryRepository.findByMember_MemberIdOrderByPublishedAtDesc(memberId, pageable);
  }

  // ==================== Action 메서드 ====================

  /**
   * 강제탈퇴
   */
  @Transactional
  public void forceWithdrawMember(UUID targetMemberId, String forceWithdrawReason, UUID executorAdminId) {
    validateAdminAction(targetMemberId, executorAdminId);

    Member targetMember = memberRepository.findById(targetMemberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    if (targetMember.getAccountStatus() == AccountStatus.DELETE_ACCOUNT) {
      throw new CustomException(ErrorCode.MEMBER_ALREADY_DELETED);
    }

    LocalDateTime executedAt = LocalDateTime.now(ZoneOffset.UTC);

    // 제재 이력 (FORCE_WITHDRAW) 기록
    SanctionHistory forceWithdrawSanction = SanctionHistory.builder()
        .memberId(targetMemberId)
        .suspendReason(forceWithdrawReason)
        .suspendedAt(executedAt)
        .suspendedUntil(AccountStatus.PERMANENT_SUSPENSION_UNTIL)
        .sanctionType(SanctionType.FORCE_WITHDRAW)
        .executorAdminId(executorAdminId)
        .build();
    sanctionHistoryRepository.save(forceWithdrawSanction);

    // 회원 보유 물품 일괄 soft delete
    try {
      itemService.softDeleteAllByMember(targetMemberId);
    } catch (Exception softDeleteItemsException) {
      log.warn("강제탈퇴 - 회원 물품 일괄 삭제 실패: memberId={}, error={}", targetMemberId, softDeleteItemsException.getMessage());
    }

    // 진행중 거래 일괄 CANCELED
    try {
      int canceledTradeCount = tradeRequestHistoryRepository.cancelAllOngoingByMemberId(targetMemberId);
      log.info("강제탈퇴 - 진행중 거래 CANCELED 처리: memberId={}, canceledCount={}", targetMemberId, canceledTradeCount);
    } catch (Exception cancelOngoingTradesException) {
      log.warn("강제탈퇴 - 진행중 거래 취소 실패: memberId={}, error={}", targetMemberId, cancelOngoingTradesException.getMessage());
    }

    // MemberService.deleteMemberRelatedData 재사용 (위치/카테고리 정리 + status=DELETE_ACCOUNT + email=null)
    try {
      memberService.deleteMemberRelatedData(MemberRequest.builder().member(targetMember).build());
    } catch (Exception deleteRelatedDataException) {
      log.warn("강제탈퇴 - 회원 관련 데이터 정리 실패: memberId={}, error={}", targetMemberId, deleteRelatedDataException.getMessage());
      // 보강: 직접 상태 변경
      targetMember.setAccountStatus(AccountStatus.DELETE_ACCOUNT);
      targetMember.setEmail(null);
      memberRepository.save(targetMember);
    }

    // 토큰 무효화 (RefreshToken 삭제)
    String refreshTokenRedisKey = REFRESH_KEY_PREFIX + targetMemberId;
    redisTemplate.delete(refreshTokenRedisKey);

    log.info("관리자 강제탈퇴 완료: targetMemberId={}, executorAdminId={}, reason={}",
        targetMemberId, executorAdminId, forceWithdrawReason);
  }

  /**
   * 물품 일괄 삭제
   */
  @Transactional
  public List<BulkActionResult> bulkDeleteItems(UUID targetMemberId, List<UUID> itemIds, String reason, UUID executorAdminId) {
    validateAdminAction(targetMemberId, executorAdminId);

    if (itemIds == null || itemIds.isEmpty()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    List<BulkActionResult> bulkActionResults = new ArrayList<>();
    List<UUID> successfullyDeletedItemIds = new ArrayList<>();

    for (UUID targetItemId : itemIds) {
      try {
        itemService.softDeleteByAdmin(
            targetItemId,
            targetMemberId,
            ItemAdminDeleteReason.ETC,
            reason
        );
        bulkActionResults.add(BulkActionResult.builder()
            .targetId(targetItemId)
            .isSuccess(true)
            .build());
        successfullyDeletedItemIds.add(targetItemId);
      } catch (CustomException customException) {
        bulkActionResults.add(BulkActionResult.builder()
            .targetId(targetItemId)
            .isSuccess(false)
            .failReason(customException.getErrorCode().name())
            .build());
      } catch (Exception unexpectedException) {
        bulkActionResults.add(BulkActionResult.builder()
            .targetId(targetItemId)
            .isSuccess(false)
            .failReason(unexpectedException.getClass().getSimpleName())
            .build());
      }
    }

    // 일괄 삭제 1건의 감사 이력
    String successItemIdSummary = successfullyDeletedItemIds.toString();
    SanctionHistory bulkDeleteSanction = SanctionHistory.builder()
        .memberId(targetMemberId)
        .suspendReason("[BULK_DELETE_ITEMS] 사유=" + reason + " 성공=" + successItemIdSummary)
        .suspendedAt(LocalDateTime.now(ZoneOffset.UTC))
        .sanctionType(SanctionType.BULK_DELETE_ITEMS)
        .executorAdminId(executorAdminId)
        .build();
    sanctionHistoryRepository.save(bulkDeleteSanction);

    log.info("관리자 물품 일괄 삭제 완료: targetMemberId={}, totalRequested={}, success={}",
        targetMemberId, itemIds.size(), successfullyDeletedItemIds.size());

    return bulkActionResults;
  }

  /**
   * 회원에게 알림 발송 (관리자 → 회원 단건)
   */
  @Transactional
  public void sendNotificationToMember(UUID targetMemberId, String title, String content, String notificationTypeName, UUID executorAdminId) {
    validateAdminAction(targetMemberId, executorAdminId);

    if (!StringUtils.hasText(title) || title.length() > 100) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    if (!StringUtils.hasText(content) || content.length() > 1000) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    NotificationType resolvedNotificationType;
    try {
      resolvedNotificationType = notificationTypeName != null
          ? NotificationType.valueOf(notificationTypeName)
          : NotificationType.ANNOUNCEMENT;
    } catch (IllegalArgumentException invalidTypeException) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    LocalDateTime publishedAt = LocalDateTime.now();
    Map<String, String> notificationPayload = new HashMap<>();
    notificationPayload.put("notificationType", resolvedNotificationType.name());
    notificationPayload.put("publishedAt", publishedAt.toString());

    try {
      notificationService.sendToMember(targetMemberId, title, content, notificationPayload);
    } catch (Exception sendNotificationException) {
      log.warn("관리자 알림 발송 실패: targetMemberId={}, error={}", targetMemberId, sendNotificationException.getMessage());
    }

    // 감사 로그
    SanctionHistory notificationAuditSanction = SanctionHistory.builder()
        .memberId(targetMemberId)
        .suspendReason("[ADMIN_NOTIFICATION] title=" + title)
        .suspendedAt(LocalDateTime.now(ZoneOffset.UTC))
        .sanctionType(SanctionType.ADMIN_NOTIFICATION)
        .executorAdminId(executorAdminId)
        .build();
    sanctionHistoryRepository.save(notificationAuditSanction);

    log.info("관리자 알림 발송 완료: targetMemberId={}, executorAdminId={}, type={}",
        targetMemberId, executorAdminId, resolvedNotificationType);
  }

  // ==================== 공통 검증 ====================

  private void validateAdminAction(UUID targetMemberId, UUID executorAdminId) {
    if (targetMemberId == null || executorAdminId == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    if (targetMemberId.equals(executorAdminId)) {
      throw new CustomException(ErrorCode.ADMIN_SELF_ACTION_FORBIDDEN);
    }
    Member targetMember = memberRepository.findById(targetMemberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    if (targetMember.getRole() == Role.ROLE_ADMIN) {
      throw new CustomException(ErrorCode.ADMIN_TARGET_FORBIDDEN);
    }
  }
}
