package com.romrom.application.service;

import static com.romrom.auth.jwt.JwtUtil.REFRESH_KEY_PREFIX;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.dto.BulkActionResult;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.ItemAdminDeleteReason;
import com.romrom.common.constant.Role;
import com.romrom.common.constant.SanctionType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.member.repository.MemberRepository;
import com.romrom.member.repository.mongo.SanctionHistoryRepository;
import com.romrom.member.service.MemberService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 제재: 상태 변경, 정지·해제, 강제탈퇴, 일괄 정지·탈퇴·물품 삭제, 제재 대상 검증
 * 일괄 작업은 건별 독립 트랜잭션 (self 프록시·AdminItemDeletionService 프록시 경유)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMemberSanctionService {

  private final MemberRepository memberRepository;
  private final MemberReportRepository memberReportRepository;
  private final ItemReportRepository itemReportRepository;
  private final SanctionHistoryRepository sanctionHistoryRepository;
  private final RedisTemplate<String, Object> redisTemplate;
  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final AdminItemDeletionService adminItemDeletionService;
  private final MemberService memberService;

  // #713 일괄 작업: 각 회원을 독립 트랜잭션으로 처리하기 위한 self-injection
  // (같은 클래스 메서드를 this로 호출하면 @Transactional 프록시가 적용되지 않아 부분 실패 격리가 안 됨)
  @Lazy
  @Autowired
  private AdminMemberSanctionService self;

  // 상태 변경 화면에서 "삭제"를 고른 경우의 강제탈퇴 사유
  private static final String STATUS_CHANGE_WITHDRAW_REASON = "관리자 상태 변경(삭제)";

  @Transactional
  public AdminResponse updateMemberStatusForAdmin(AdminRequest request) {
    Member member = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    validateSanctionTarget(member, request.getExecutorAdminId());

    if (request.getAccountStatus() == null || request.getAccountStatus() == AccountStatus.SUSPENDED_ACCOUNT) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    // 정지 중 > 활성: 정지 해제 경로 (사유·기한 정리, 제재 이력 닫기)
    if (member.getAccountStatus() == AccountStatus.SUSPENDED_ACCOUNT
        && request.getAccountStatus() == AccountStatus.ACTIVE_ACCOUNT) {
      return unsuspendMember(request);
    }

    // 삭제: 강제탈퇴 경로 (물품·거래 정리, 토큰 무효화, 제재 이력)
    if (request.getAccountStatus() == AccountStatus.DELETE_ACCOUNT) {
      forceWithdrawMember(member.getMemberId(), STATUS_CHANGE_WITHDRAW_REASON, request.getExecutorAdminId());
      return AdminResponse.builder().member(memberRepository.findById(member.getMemberId()).orElse(member)).build();
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
    validateSanctionTarget(targetMember, request.getExecutorAdminId());

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
        .findLatestActiveSuspension(targetMember.getMemberId());
    if (activeSanctionHistory.isPresent()) {
      SanctionHistory previousSanction = activeSanctionHistory.get();
      previousSanction.setLiftedAt(LocalDateTime.now(ZoneOffset.UTC));
      previousSanction.setLiftedReason("제재 변경");
      previousSanction.setLiftedByAdminId(request.getExecutorAdminId());
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
        .executorAdminId(request.getExecutorAdminId())
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
        .findLatestActiveSuspension(targetMember.getMemberId());
    if (activeSanctionHistory.isPresent()) {
      SanctionHistory sanctionToLift = activeSanctionHistory.get();
      sanctionToLift.setLiftedAt(LocalDateTime.now(ZoneOffset.UTC));
      sanctionToLift.setLiftedReason("수동 해제");
      sanctionToLift.setLiftedByAdminId(request.getExecutorAdminId());
      sanctionHistoryRepository.save(sanctionToLift);
    }

    targetMember.setAccountStatus(AccountStatus.ACTIVE_ACCOUNT);
    targetMember.setSuspendReason(null);
    targetMember.setSuspendedAt(null);
    targetMember.setSuspendedUntil(null);
    memberRepository.save(targetMember);

    return AdminResponse.builder().member(targetMember).build();
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

    // 하위 정리 실패 시 전체 실패 (같은 트랜잭션이라 catch 후 진행하면 rollback-only로 커밋 실패)
    // 회원 보유 물품 일괄 soft delete
    adminItemDeletionService.softDeleteAllByMember(targetMemberId);

    // 진행중 거래 일괄 CANCELED
    int canceledTradeCount = tradeRequestHistoryRepository.cancelAllOngoingByMemberId(targetMemberId);
    log.info("강제탈퇴 - 진행중 거래 CANCELED 처리: memberId={}, canceledCount={}", targetMemberId, canceledTradeCount);

    // 위치·카테고리 정리 + status=DELETE_ACCOUNT + email=null
    memberService.deleteMemberRelatedData(MemberRequest.builder().member(targetMember).build());

    // 제재 이력 (FORCE_WITHDRAW): Mongo는 JPA 롤백 대상이 아니라 정리 성공 후 기록
    SanctionHistory forceWithdrawSanction = SanctionHistory.builder()
        .memberId(targetMemberId)
        .suspendReason(forceWithdrawReason)
        .suspendedAt(LocalDateTime.now(ZoneOffset.UTC))
        .suspendedUntil(AccountStatus.PERMANENT_SUSPENSION_UNTIL)
        .sanctionType(SanctionType.FORCE_WITHDRAW)
        .executorAdminId(executorAdminId)
        .build();
    sanctionHistoryRepository.save(forceWithdrawSanction);

    // 토큰 무효화 (RefreshToken 삭제)
    String refreshTokenRedisKey = REFRESH_KEY_PREFIX + targetMemberId;
    redisTemplate.delete(refreshTokenRedisKey);

    log.info("관리자 강제탈퇴 완료: targetMemberId={}, executorAdminId={}, reason={}",
        targetMemberId, executorAdminId, forceWithdrawReason);
  }

  /**
   * 물품 일괄 삭제: 건별 독립 트랜잭션(AdminItemDeletionService.softDeleteByAdmin) > 개별 결과 반환
   * 메서드 자체는 트랜잭션 없음 (1건 실패가 성공 건까지 롤백하지 않음)
   */
  public List<BulkActionResult> bulkDeleteItems(UUID targetMemberId, List<UUID> itemIds, String reason, UUID executorAdminId) {
    validateAdminAction(targetMemberId, executorAdminId);

    if (itemIds == null || itemIds.isEmpty()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    List<BulkActionResult> bulkActionResults = new ArrayList<>();
    List<UUID> successfullyDeletedItemIds = new ArrayList<>();

    for (UUID targetItemId : itemIds) {
      try {
        adminItemDeletionService.softDeleteByAdmin(
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
   * 회원 일괄 정지 (#713)
   * - 각 회원을 독립 트랜잭션(self.suspendMember)으로 처리해 부분 실패를 격리한다.
   * - 한 건 실패해도 나머지는 계속 처리하고, 개별 결과를 BulkActionResult로 반환한다.
   * - bulk 메서드 자체에는 @Transactional을 두지 않는다 (전체 롤백 방지).
   */
  public List<BulkActionResult> bulkSuspendMembers(List<UUID> memberIds, String suspendReason,
      String suspendedUntil, UUID executorAdminId) {
    if (memberIds == null || memberIds.isEmpty()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    List<BulkActionResult> bulkActionResults = new ArrayList<>();
    for (UUID targetMemberId : memberIds) {
      try {
        AdminRequest suspendRequest = AdminRequest.builder()
            .memberId(targetMemberId)
            .suspendReason(suspendReason)
            .suspendedUntil(suspendedUntil)
            .executorAdminId(executorAdminId)
            .build();
        // self 호출로 @Transactional 프록시 경유 → 회원 단위 독립 트랜잭션
        self.suspendMember(suspendRequest);
        bulkActionResults.add(BulkActionResult.builder()
            .targetId(targetMemberId)
            .isSuccess(true)
            .build());
      } catch (CustomException customException) {
        bulkActionResults.add(BulkActionResult.builder()
            .targetId(targetMemberId)
            .isSuccess(false)
            .failReason(customException.getErrorCode().name())
            .build());
      } catch (Exception unexpectedException) {
        bulkActionResults.add(BulkActionResult.builder()
            .targetId(targetMemberId)
            .isSuccess(false)
            .failReason(unexpectedException.getClass().getSimpleName())
            .build());
      }
    }

    long successCount = bulkActionResults.stream().filter(BulkActionResult::getIsSuccess).count();
    log.info("관리자 회원 일괄 정지 완료: totalRequested={}, success={}", memberIds.size(), successCount);
    return bulkActionResults;
  }

  /**
   * 회원 일괄 강제 탈퇴 (#713)
   * - 각 회원을 독립 트랜잭션(self.forceWithdrawMember)으로 처리해 부분 실패를 격리한다.
   */
  public List<BulkActionResult> bulkWithdrawMembers(List<UUID> memberIds, String forceWithdrawReason,
      UUID executorAdminId) {
    if (memberIds == null || memberIds.isEmpty()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    List<BulkActionResult> bulkActionResults = new ArrayList<>();
    for (UUID targetMemberId : memberIds) {
      try {
        // self 호출로 @Transactional 프록시 경유 → 회원 단위 독립 트랜잭션 + cascade 로직 재사용
        self.forceWithdrawMember(targetMemberId, forceWithdrawReason, executorAdminId);
        bulkActionResults.add(BulkActionResult.builder()
            .targetId(targetMemberId)
            .isSuccess(true)
            .build());
      } catch (CustomException customException) {
        bulkActionResults.add(BulkActionResult.builder()
            .targetId(targetMemberId)
            .isSuccess(false)
            .failReason(customException.getErrorCode().name())
            .build());
      } catch (Exception unexpectedException) {
        bulkActionResults.add(BulkActionResult.builder()
            .targetId(targetMemberId)
            .isSuccess(false)
            .failReason(unexpectedException.getClass().getSimpleName())
            .build());
      }
    }

    long successCount = bulkActionResults.stream().filter(BulkActionResult::getIsSuccess).count();
    log.info("관리자 회원 일괄 탈퇴 완료: totalRequested={}, success={}", memberIds.size(), successCount);
    return bulkActionResults;
  }

  /**
   * 관리자 조치 공통 검증: 필수값 > 본인 대상 금지 > 회원 존재 > 제재 대상 검증
   */
  public void validateAdminAction(UUID targetMemberId, UUID executorAdminId) {
    if (targetMemberId == null || executorAdminId == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    if (targetMemberId.equals(executorAdminId)) {
      throw new CustomException(ErrorCode.ADMIN_SELF_ACTION_FORBIDDEN);
    }
    Member targetMember = memberRepository.findById(targetMemberId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    validateSanctionTarget(targetMember, executorAdminId);
  }

  /**
   * 제재 대상 검증 2개: 처리자 본인 금지, 관리자 계정 금지 (처리자 미지정이면 관리자 여부만)
   */
  private void validateSanctionTarget(Member targetMember, UUID executorAdminId) {
    if (targetMember.getMemberId().equals(executorAdminId)) {
      throw new CustomException(ErrorCode.ADMIN_SELF_ACTION_FORBIDDEN);
    }
    if (targetMember.getRole() == Role.ROLE_ADMIN) {
      throw new CustomException(ErrorCode.ADMIN_TARGET_FORBIDDEN);
    }
  }
}
