package com.romrom.member.repository;

import com.romrom.common.constant.AccountStatus;
import com.romrom.common.dto.DailyCountProjection;
import com.romrom.common.dto.GroupCountProjection;
import com.romrom.member.entity.Member;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, UUID> {

  Optional<Member> findByEmail(String email);

  Optional<Member> findByFirebaseUid(String firebaseUid);

  Optional<Member> findByEmailAndIsDeletedFalse(String email);

  Boolean existsByNicknameAndMemberIdNot(String nickname, UUID memberId);

  @Modifying
  @Query("update Member m set m.isDeleted = true, m.accountStatus = 'DELETE_ACCOUNT' where m.memberId = :memberId")
  void deleteByMemberId(UUID memberId);
  
  long countByIsDeletedFalse();
  
  @Query("SELECT COUNT(m) FROM Member m WHERE m.isDeleted = false AND m.accountStatus != 'DELETE_ACCOUNT'")
  long countActiveMembers();
  
  Page<Member> findByIsDeletedFalse(Pageable pageable);

  Page<Member> findByAccountStatusAndIsDeletedFalse(AccountStatus accountStatus, Pageable pageable);

  // #713 통합 검색: 닉네임/이메일에 더해 memberId(UUID 문자열) 매칭 추가
  @Query("SELECT m FROM Member m WHERE m.isDeleted = false AND " +
         "(LOWER(m.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
         "LOWER(m.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
         "LOWER(CAST(m.memberId AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')))")
  Page<Member> searchByKeywordAndIsDeletedFalse(@Param("keyword") String keyword, Pageable pageable);

  @Query("SELECT m FROM Member m WHERE m.isDeleted = false AND m.accountStatus = :accountStatus AND " +
         "(LOWER(m.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
         "LOWER(m.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
         "LOWER(CAST(m.memberId AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')))")
  Page<Member> searchByKeywordAndAccountStatusAndIsDeletedFalse(
      @Param("keyword") String keyword,
      @Param("accountStatus") AccountStatus accountStatus,
      Pageable pageable);

  // suspendedUntil >= permanentThreshold 이면 영구정지, 그 미만이면 일시정지
  @Query("SELECT m FROM Member m WHERE m.isDeleted = false AND m.accountStatus = :accountStatus " +
         "AND m.suspendedUntil >= :permanentThreshold")
  Page<Member> findPermanentSuspendedMembers(
      @Param("accountStatus") AccountStatus accountStatus,
      @Param("permanentThreshold") LocalDateTime permanentThreshold,
      Pageable pageable);

  @Query("SELECT m FROM Member m WHERE m.isDeleted = false AND m.accountStatus = :accountStatus " +
         "AND m.suspendedUntil < :permanentThreshold")
  Page<Member> findTemporarySuspendedMembers(
      @Param("accountStatus") AccountStatus accountStatus,
      @Param("permanentThreshold") LocalDateTime permanentThreshold,
      Pageable pageable);

  @Query("SELECT m FROM Member m WHERE m.isDeleted = false AND m.accountStatus = :accountStatus " +
         "AND m.suspendedUntil >= :permanentThreshold AND " +
         "(LOWER(m.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
         "LOWER(m.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
         "LOWER(CAST(m.memberId AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')))")
  Page<Member> searchPermanentSuspendedMembers(
      @Param("keyword") String keyword,
      @Param("accountStatus") AccountStatus accountStatus,
      @Param("permanentThreshold") LocalDateTime permanentThreshold,
      Pageable pageable);

  @Query("SELECT m FROM Member m WHERE m.isDeleted = false AND m.accountStatus = :accountStatus " +
         "AND m.suspendedUntil < :permanentThreshold AND " +
         "(LOWER(m.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
         "LOWER(m.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
         "LOWER(CAST(m.memberId AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')))")
  Page<Member> searchTemporarySuspendedMembers(
      @Param("keyword") String keyword,
      @Param("accountStatus") AccountStatus accountStatus,
      @Param("permanentThreshold") LocalDateTime permanentThreshold,
      Pageable pageable);

  @Modifying
  @Query("UPDATE Member m SET m.lastActiveAt = :now WHERE m.memberId = :memberId")
  void updateLastActiveAt(@Param("memberId") UUID memberId, @Param("now") LocalDateTime now);

  // ===== 관리자 대시보드 분석 (#827) =====

  /**
   * 날짜별 신규 가입 (관리자 계정 제외, 탈퇴 포함 = 가입 시점 기준)
   */
  @Query(value = "SELECT TO_CHAR(created_date, 'YYYY-MM-DD') AS day, COUNT(*) AS count FROM member "
      + "WHERE created_date BETWEEN :startDateTime AND :endDateTime AND COALESCE(role, '') <> 'ROLE_ADMIN' "
      + "GROUP BY TO_CHAR(created_date, 'YYYY-MM-DD')", nativeQuery = true)
  List<DailyCountProjection> countDailyNewMembers(
      @Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime);

  /**
   * 기준 시각 이후 활동 회원 수 (DAU·WAU·MAU, 탈퇴·관리자 제외)
   */
  @Query(value = "SELECT COUNT(*) FROM member WHERE last_active_at >= :activeSince AND is_deleted = false "
      + "AND COALESCE(role, '') <> 'ROLE_ADMIN'", nativeQuery = true)
  long countActiveMembersSince(@Param("activeSince") LocalDateTime activeSince);

  /**
   * 가입 경로별 회원 수 (탈퇴·관리자 제외, 미지정은 UNKNOWN)
   */
  @Query(value = "SELECT COALESCE(social_platform, 'UNKNOWN') AS group_key, COUNT(*) AS count FROM member "
      + "WHERE is_deleted = false AND COALESCE(role, '') <> 'ROLE_ADMIN' GROUP BY COALESCE(social_platform, 'UNKNOWN')",
      nativeQuery = true)
  List<GroupCountProjection> countGroupedBySocialPlatform();
}
