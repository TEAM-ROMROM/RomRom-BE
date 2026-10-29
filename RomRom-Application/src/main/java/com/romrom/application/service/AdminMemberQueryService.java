package com.romrom.application.service;

import com.romrom.ai.entity.mongo.AiUsageHistory;
import com.romrom.ai.repository.mongo.AiUsageHistoryRepository;
import com.romrom.application.dto.AdminMemberDetail360Dto;
import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.AiUsageType;
import com.romrom.common.constant.LoginResult;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.mongo.LikeHistory;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.mongo.LikeHistoryRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.mongo.LoginHistory;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.member.repository.MemberRepository;
import com.romrom.member.repository.mongo.LoginHistoryRepository;
import com.romrom.member.repository.mongo.SanctionHistoryRepository;
import com.romrom.notification.entity.NotificationHistory;
import com.romrom.notification.repository.NotificationHistoryRepository;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.entity.MemberReport;
import com.romrom.report.repository.ItemReportRepository;
import com.romrom.report.repository.MemberReportRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 관리자 회원 조회: 목록·최근 가입·상세, 제재 이력, 회원 360 하위 목록 페이지
 * 모든 메서드 읽기 전용 트랜잭션
 */
@Service
@RequiredArgsConstructor
public class AdminMemberQueryService {

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
  private final AdminMemberDetail360Service adminMemberDetail360Service;

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

    AdminMemberDetail360Dto memberDetail360Dto = adminMemberDetail360Service.getMemberDetail360(member.getMemberId());

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
    // loginResult 미지정: 전체, 지정: SUCCESS/FAIL만 (페이지·total 모두 필터 기준)
    if (loginResult == null) {
      return loginHistoryRepository.findByMemberIdOrderByLoginAtDesc(memberId, pageable);
    }
    return loginHistoryRepository.findByMemberIdAndLoginResultOrderByLoginAtDesc(memberId, loginResult, pageable);
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
}
