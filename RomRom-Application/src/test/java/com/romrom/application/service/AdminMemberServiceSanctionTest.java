package com.romrom.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.BulkActionResult;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.ItemAdminDeleteReason;
import com.romrom.common.constant.LoginResult;
import com.romrom.common.constant.Role;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.member.repository.MemberRepository;
import com.romrom.member.repository.mongo.LoginHistoryRepository;
import com.romrom.member.repository.mongo.SanctionHistoryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 제재 정합성 회귀 검증 (#814): 대상 검증, 처리자 기록, 건별 실패 격리, 로그인 이력 필터
 * Mockito 단위 테스트 (DB·Redis 불필요)
 */
@ExtendWith(MockitoExtension.class)
class AdminMemberServiceSanctionTest {

  @Mock private MemberRepository memberRepository;
  @Mock private SanctionHistoryRepository sanctionHistoryRepository;
  @Mock private RedisTemplate<String, Object> redisTemplate;
  @Mock private AdminItemDeletionService adminItemDeletionService;
  @Mock private LoginHistoryRepository loginHistoryRepository;

  @InjectMocks private AdminMemberSanctionService adminMemberSanctionService;
  @InjectMocks private AdminMemberQueryService adminMemberQueryService;

  private final UUID executorAdminId = UUID.randomUUID();

  @Test
  @DisplayName("정지: 관리자 계정은 ADMIN_TARGET_FORBIDDEN, 회원 상태 변경 없음")
  void 관리자_계정_정지는_거부된다() {
    Member adminMember = memberWithRole(Role.ROLE_ADMIN);
    when(memberRepository.findById(adminMember.getMemberId())).thenReturn(Optional.of(adminMember));

    CustomException sanctionException = assertThrows(CustomException.class,
        () -> adminMemberSanctionService.suspendMember(suspendRequestFor(adminMember.getMemberId())));

    assertEquals(ErrorCode.ADMIN_TARGET_FORBIDDEN, sanctionException.getErrorCode());
    verify(memberRepository, never()).save(any());
  }

  @Test
  @DisplayName("정지: 처리자 본인은 ADMIN_SELF_ACTION_FORBIDDEN")
  void 처리자_본인_정지는_거부된다() {
    Member selfMember = memberWithRole(Role.ROLE_USER);
    selfMember.setMemberId(executorAdminId);
    when(memberRepository.findById(executorAdminId)).thenReturn(Optional.of(selfMember));

    CustomException sanctionException = assertThrows(CustomException.class,
        () -> adminMemberSanctionService.suspendMember(suspendRequestFor(executorAdminId)));

    assertEquals(ErrorCode.ADMIN_SELF_ACTION_FORBIDDEN, sanctionException.getErrorCode());
  }

  @Test
  @DisplayName("정지: 제재 이력에 처리자(executorAdminId) 기록")
  void 정지_이력에_처리자가_기록된다() {
    Member targetMember = memberWithRole(Role.ROLE_USER);
    when(memberRepository.findById(targetMember.getMemberId())).thenReturn(Optional.of(targetMember));
    when(sanctionHistoryRepository.findFirstByMemberIdAndLiftedAtIsNullOrderBySuspendedAtDesc(targetMember.getMemberId()))
        .thenReturn(Optional.empty());

    adminMemberSanctionService.suspendMember(suspendRequestFor(targetMember.getMemberId()));

    ArgumentCaptor<SanctionHistory> sanctionCaptor = ArgumentCaptor.forClass(SanctionHistory.class);
    verify(sanctionHistoryRepository).save(sanctionCaptor.capture());
    assertEquals(executorAdminId, sanctionCaptor.getValue().getExecutorAdminId());
    assertEquals(AccountStatus.SUSPENDED_ACCOUNT, targetMember.getAccountStatus());
  }

  @Test
  @DisplayName("강제탈퇴: 물품 정리 실패 시 예외 전파, 제재 이력 미기록")
  void 강제탈퇴_하위_정리_실패는_전체_실패다() {
    Member targetMember = memberWithRole(Role.ROLE_USER);
    when(memberRepository.findById(targetMember.getMemberId())).thenReturn(Optional.of(targetMember));
    doThrow(new IllegalStateException("embedding delete failed"))
        .when(adminItemDeletionService).softDeleteAllByMember(targetMember.getMemberId());

    assertThrows(IllegalStateException.class,
        () -> adminMemberSanctionService.forceWithdrawMember(targetMember.getMemberId(), "reason", executorAdminId));

    verify(sanctionHistoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("물품 일괄삭제: 1건 실패해도 나머지 성공, 메서드 자체 트랜잭션 없음")
  void 물품_일괄삭제는_건별로_격리된다() throws Exception {
    Member targetMember = memberWithRole(Role.ROLE_USER);
    when(memberRepository.findById(targetMember.getMemberId())).thenReturn(Optional.of(targetMember));
    UUID firstItemId = UUID.randomUUID();
    UUID failingItemId = UUID.randomUUID();
    UUID lastItemId = UUID.randomUUID();
    // 다른 itemId 호출도 허용 (strict stubs 인자 불일치 예외 방지)
    lenient().doThrow(new CustomException(ErrorCode.INVALID_ITEM_OWNER)).when(adminItemDeletionService)
        .softDeleteByAdmin(eq(failingItemId), eq(targetMember.getMemberId()), eq(ItemAdminDeleteReason.ETC), anyString());

    List<BulkActionResult> bulkActionResults = adminMemberSanctionService.bulkDeleteItems(
        targetMember.getMemberId(), List.of(firstItemId, failingItemId, lastItemId), "reason", executorAdminId);

    assertTrue(bulkActionResults.get(0).getIsSuccess());
    assertFalse(bulkActionResults.get(1).getIsSuccess());
    assertEquals(ErrorCode.INVALID_ITEM_OWNER.name(), bulkActionResults.get(1).getFailReason());
    assertTrue(bulkActionResults.get(2).getIsSuccess());
    assertFalse(AdminMemberSanctionService.class
        .getMethod("bulkDeleteItems", UUID.class, List.class, String.class, UUID.class)
        .isAnnotationPresent(Transactional.class));
  }

  @Test
  @DisplayName("로그인 이력: 결과 필터는 쿼리에서 적용 (total 정확)")
  void 로그인_이력_결과_필터는_쿼리로_조회한다() {
    UUID memberId = UUID.randomUUID();
    Pageable pageable = PageRequest.of(0, 20);

    adminMemberQueryService.listLoginHistory(memberId, LoginResult.FAIL, pageable);

    verify(loginHistoryRepository).findByMemberIdAndLoginResultOrderByLoginAtDesc(memberId, LoginResult.FAIL, pageable);
    verify(loginHistoryRepository, never()).findByMemberIdOrderByLoginAtDesc(any(), any());
  }

  private Member memberWithRole(Role role) {
    return Member.builder()
        .memberId(UUID.randomUUID())
        .role(role)
        .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
        .build();
  }

  private AdminRequest suspendRequestFor(UUID targetMemberId) {
    return AdminRequest.builder()
        .memberId(targetMemberId)
        .suspendReason("reason")
        .executorAdminId(executorAdminId)
        .build();
  }
}
