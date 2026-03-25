package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.member.repository.MemberRepository;
import com.romrom.member.repository.mongo.SanctionHistoryRepository;
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
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
        log.debug("회원 목록 검색+상태+정지유형 조회: keyword={}, accountStatus={}, suspendType={}, page={}, size={}",
            trimmedKeyword, request.getAccountStatus(), request.getSuspendType(), request.getPageNumber(), request.getPageSize());
        memberPage = isPermanentFilter
            ? memberRepository.searchPermanentSuspendedMembers(trimmedKeyword, request.getAccountStatus(), AccountStatus.PERMANENT_SUSPENSION_UNTIL, pageable)
            : memberRepository.searchTemporarySuspendedMembers(trimmedKeyword, request.getAccountStatus(), AccountStatus.PERMANENT_SUSPENSION_UNTIL, pageable);
      } else {
        log.debug("회원 목록 상태+정지유형 조회: accountStatus={}, suspendType={}, page={}, size={}",
            request.getAccountStatus(), request.getSuspendType(), request.getPageNumber(), request.getPageSize());
        memberPage = isPermanentFilter
            ? memberRepository.findPermanentSuspendedMembers(request.getAccountStatus(), AccountStatus.PERMANENT_SUSPENSION_UNTIL, pageable)
            : memberRepository.findTemporarySuspendedMembers(request.getAccountStatus(), AccountStatus.PERMANENT_SUSPENSION_UNTIL, pageable);
      }
    } else if (hasKeyword && hasAccountStatusFilter) {
      log.debug("회원 목록 검색+상태 조회: keyword={}, accountStatus={}, page={}, size={}",
          trimmedKeyword, request.getAccountStatus(), request.getPageNumber(), request.getPageSize());
      memberPage = memberRepository.searchByKeywordAndAccountStatusAndIsDeletedFalse(
          trimmedKeyword, request.getAccountStatus(), pageable);
    } else if (hasKeyword) {
      log.debug("회원 목록 검색 조회: keyword={}, page={}, size={}",
          trimmedKeyword, request.getPageNumber(), request.getPageSize());
      memberPage = memberRepository.searchByKeywordAndIsDeletedFalse(trimmedKeyword, pageable);
    } else if (hasAccountStatusFilter) {
      log.debug("회원 목록 상태 필터 조회: accountStatus={}, page={}, size={}",
          request.getAccountStatus(), request.getPageNumber(), request.getPageSize());
      memberPage = memberRepository.findByAccountStatusAndIsDeletedFalse(request.getAccountStatus(), pageable);
    } else {
      log.debug("회원 목록 전체 조회: page={}, size={}", request.getPageNumber(), request.getPageSize());
      memberPage = memberRepository.findByIsDeletedFalse(pageable);
    }

    log.info("회원 목록 조회 완료: totalElements={}, page={}/{}",
        memberPage.getTotalElements(), memberPage.getNumber(), memberPage.getTotalPages());

    return AdminResponse.builder()
        .members(memberPage)
        .totalCount(memberPage.getTotalElements())
        .build();
  }

  /**
   * 최근 가입 회원 조회 (관리자 대시보드용)
   */
  @Transactional(readOnly = true)
  public AdminResponse getRecentMembersForAdmin(int limit) {
    log.debug("최근 가입 회원 조회: limit={}", limit);
    Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdDate"));
    Page<Member> memberPage = memberRepository.findByIsDeletedFalse(pageable);

    log.info("최근 가입 회원 조회 완료: count={}", memberPage.getContent().size());

    return AdminResponse.builder()
        .members(memberPage)
        .totalCount(memberPage.getTotalElements())
        .build();
  }

  /**
   * 관리자용 회원 상세 조회 (기본 정보 + 전체 물품 + 신고당한 내역)
   */
  @Transactional(readOnly = true)
  public AdminResponse getMemberDetailForAdmin(AdminRequest request) {
    log.debug("회원 상세 조회: memberId={}", request.getMemberId());

    Member member = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> {
          log.error("회원을 찾을 수 없음: memberId={}", request.getMemberId());
          return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        });

    List<Item> items = itemRepository.findByMemberAndIsDeletedFalseOrderByCreatedDateDesc(member);
    List<MemberReport> memberReports = memberReportRepository.findByTargetMemberOrderByCreatedDateDesc(member);

    log.info("회원 상세 조회 완료: memberId={}, itemCount={}, reportCount={}",
        member.getMemberId(), items.size(), memberReports.size());

    return AdminResponse.builder()
        .memberDetail(AdminResponse.AdminMemberDetailDto.builder()
            .member(member)
            .items(items)
            .memberReports(memberReports)
            .reportCount((long) memberReports.size())
            .build())
        .build();
  }

  /**
   * 관리자용 회원 상태 변경
   */
  @Transactional
  public AdminResponse updateMemberStatusForAdmin(AdminRequest request) {
    log.debug("회원 상태 변경: memberId={}, accountStatus={}", request.getMemberId(), request.getAccountStatus());

    Member member = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> {
          log.error("회원을 찾을 수 없음: memberId={}", request.getMemberId());
          return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        });

    if (request.getAccountStatus() == AccountStatus.SUSPENDED_ACCOUNT) {
      log.error("SUSPENDED_ACCOUNT는 suspend 전용 엔드포인트 사용 필요");
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    member.setAccountStatus(request.getAccountStatus());
    memberRepository.save(member);

    log.info("회원 상태 변경 완료: memberId={}, accountStatus={}", member.getMemberId(), member.getAccountStatus());

    return AdminResponse.builder()
        .member(member)
        .build();
  }

  /**
   * 관리자용 회원 정지 처리
   */
  @Transactional
  public AdminResponse suspendMember(AdminRequest request) {
    log.debug("회원 정지 처리: memberId={}, suspendReason={}", request.getMemberId(), request.getSuspendReason());

    Member targetMember = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> {
          log.error("회원을 찾을 수 없음: memberId={}", request.getMemberId());
          return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        });

    // 제재 상태 설정
    targetMember.setAccountStatus(AccountStatus.SUSPENDED_ACCOUNT);
    targetMember.setSuspendReason(request.getSuspendReason());
    targetMember.setSuspendedAt(LocalDateTime.now(ZoneOffset.UTC));

    // 해제 예정일 파싱 (생략 시 영구 정지)
    if (request.getSuspendedUntil() != null && !request.getSuspendedUntil().isBlank()) {
      try {
        DateTimeFormatter suspendDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        targetMember.setSuspendedUntil(LocalDateTime.parse(request.getSuspendedUntil(), suspendDateTimeFormatter));
      } catch (DateTimeParseException e) {
        log.error("정지 해제 예정일 파싱 실패: {}", request.getSuspendedUntil());
        throw new CustomException(ErrorCode.INVALID_REQUEST);
      }
    } else {
      targetMember.setSuspendedUntil(AccountStatus.PERMANENT_SUSPENSION_UNTIL);
    }

    memberRepository.save(targetMember);

    // 기존 활성 제재 이력이 있으면 해제 처리 (제재 변경)
    Optional<SanctionHistory> activeSanctionHistory = sanctionHistoryRepository
        .findFirstByMemberIdAndLiftedAtIsNullOrderBySuspendedAtDesc(targetMember.getMemberId());
    if (activeSanctionHistory.isPresent()) {
      SanctionHistory previousSanction = activeSanctionHistory.get();
      previousSanction.setLiftedAt(LocalDateTime.now(ZoneOffset.UTC));
      previousSanction.setLiftedReason("제재 변경");
      sanctionHistoryRepository.save(previousSanction);
      log.debug("기존 제재 이력 해제: sanctionHistoryId={}", previousSanction.getSanctionHistoryId());
    }

    // 새 제재 이력 생성
    SanctionHistory newSanctionHistory = SanctionHistory.builder()
        .memberId(targetMember.getMemberId())
        .suspendReason(targetMember.getSuspendReason())
        .suspendedAt(targetMember.getSuspendedAt())
        .suspendedUntil(targetMember.getSuspendedUntil())
        .reportId(request.getReportId())
        .reportType(request.getReportType() != null ? request.getReportType().name() : null)
        .build();
    sanctionHistoryRepository.save(newSanctionHistory);
    log.debug("제재 이력 생성: sanctionHistoryId={}", newSanctionHistory.getSanctionHistoryId());

    // Redis에서 RefreshToken 삭제 (기존 세션 무효화)
    String refreshTokenRedisKey = "RT:" + targetMember.getMemberId();
    redisTemplate.delete(refreshTokenRedisKey);
    log.debug("RefreshToken 삭제: key={}", refreshTokenRedisKey);

    // 신고 연동: reportId가 있으면 해당 신고 상태를 COMPLETED로 변경
    if (request.getReportId() != null && request.getReportType() != null) {
      updateReportStatusToCompleted(request);
    }

    log.info("회원 정지 완료: memberId={}, suspendedUntil={}", targetMember.getMemberId(), targetMember.getSuspendedUntil());

    return AdminResponse.builder()
        .member(targetMember)
        .build();
  }

  /**
   * 관리자용 회원 정지 해제
   */
  @Transactional
  public AdminResponse unsuspendMember(AdminRequest request) {
    log.debug("회원 정지 해제: memberId={}", request.getMemberId());

    Member targetMember = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> {
          log.error("회원을 찾을 수 없음: memberId={}", request.getMemberId());
          return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        });

    if (targetMember.getAccountStatus() != AccountStatus.SUSPENDED_ACCOUNT) {
      log.error("정지 상태가 아닌 회원의 해제 시도: memberId={}, status={}", request.getMemberId(), targetMember.getAccountStatus());
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    // 활성 제재 이력에 해제 정보 업데이트
    Optional<SanctionHistory> activeSanctionHistory = sanctionHistoryRepository
        .findFirstByMemberIdAndLiftedAtIsNullOrderBySuspendedAtDesc(targetMember.getMemberId());
    if (activeSanctionHistory.isPresent()) {
      SanctionHistory sanctionToLift = activeSanctionHistory.get();
      sanctionToLift.setLiftedAt(LocalDateTime.now(ZoneOffset.UTC));
      sanctionToLift.setLiftedReason("수동 해제");
      sanctionHistoryRepository.save(sanctionToLift);
      log.debug("제재 이력 수동 해제: sanctionHistoryId={}", sanctionToLift.getSanctionHistoryId());
    }

    targetMember.setAccountStatus(AccountStatus.ACTIVE_ACCOUNT);
    targetMember.setSuspendReason(null);
    targetMember.setSuspendedAt(null);
    targetMember.setSuspendedUntil(null);
    memberRepository.save(targetMember);

    log.info("회원 정지 해제 완료: memberId={}", targetMember.getMemberId());

    return AdminResponse.builder()
        .member(targetMember)
        .build();
  }

  /**
   * 특정 회원의 제재 이력 조회 (페이지네이션)
   */
  @Transactional(readOnly = true)
  public AdminResponse getMemberSanctionHistory(AdminRequest request) {
    log.debug("회원 제재 이력 조회: memberId={}", request.getMemberId());

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "suspendedAt")
    );

    Page<SanctionHistory> sanctionHistoryPage = sanctionHistoryRepository
        .findByMemberIdOrderBySuspendedAtDesc(request.getMemberId(), pageable);

    log.info("회원 제재 이력 조회 완료: memberId={}, totalElements={}",
        request.getMemberId(), sanctionHistoryPage.getTotalElements());

    return AdminResponse.builder()
        .sanctionHistories(sanctionHistoryPage)
        .totalPages(sanctionHistoryPage.getTotalPages())
        .totalElements(sanctionHistoryPage.getTotalElements())
        .currentPage(sanctionHistoryPage.getNumber())
        .build();
  }

  /**
   * 전체 제재 이력 조회 (페이지네이션)
   */
  @Transactional(readOnly = true)
  public AdminResponse getAllSanctionHistory(AdminRequest request) {
    log.debug("전체 제재 이력 조회: page={}, size={}", request.getPageNumber(), request.getPageSize());

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "suspendedAt")
    );

    Page<SanctionHistory> sanctionHistoryPage = sanctionHistoryRepository
        .findAllByOrderBySuspendedAtDesc(pageable);

    log.info("전체 제재 이력 조회 완료: totalElements={}", sanctionHistoryPage.getTotalElements());

    return AdminResponse.builder()
        .sanctionHistories(sanctionHistoryPage)
        .totalPages(sanctionHistoryPage.getTotalPages())
        .totalElements(sanctionHistoryPage.getTotalElements())
        .currentPage(sanctionHistoryPage.getNumber())
        .build();
  }

  /**
   * 신고 상태를 COMPLETED로 변경하는 헬퍼 메서드
   */
  private void updateReportStatusToCompleted(AdminRequest request) {
    if (request.getReportType() == ReportType.ITEM) {
      ItemReport itemReport = itemReportRepository.findById(request.getReportId())
          .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));
      itemReport.setStatus(ReportStatus.COMPLETED);
      itemReportRepository.save(itemReport);
      log.debug("물품 신고 상태 COMPLETED 변경: reportId={}", request.getReportId());
    } else if (request.getReportType() == ReportType.MEMBER) {
      MemberReport memberReport = memberReportRepository.findById(request.getReportId())
          .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));
      memberReport.setStatus(ReportStatus.COMPLETED);
      memberReportRepository.save(memberReport);
      log.debug("회원 신고 상태 COMPLETED 변경: reportId={}", request.getReportId());
    }
  }
}
