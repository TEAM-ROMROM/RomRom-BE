package com.romrom.member.repository;

import com.romrom.common.constant.AccountStatus;
import com.romrom.member.entity.Member;

import java.time.LocalDateTime;
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

  @Query("SELECT m FROM Member m WHERE m.isDeleted = false AND " +
         "(LOWER(m.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
         "LOWER(m.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
  Page<Member> searchByKeywordAndIsDeletedFalse(@Param("keyword") String keyword, Pageable pageable);

  @Query("SELECT m FROM Member m WHERE m.isDeleted = false AND m.accountStatus = :accountStatus AND " +
         "(LOWER(m.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
         "LOWER(m.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
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
         "LOWER(m.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
  Page<Member> searchPermanentSuspendedMembers(
      @Param("keyword") String keyword,
      @Param("accountStatus") AccountStatus accountStatus,
      @Param("permanentThreshold") LocalDateTime permanentThreshold,
      Pageable pageable);

  @Query("SELECT m FROM Member m WHERE m.isDeleted = false AND m.accountStatus = :accountStatus " +
         "AND m.suspendedUntil < :permanentThreshold AND " +
         "(LOWER(m.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
         "LOWER(m.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
  Page<Member> searchTemporarySuspendedMembers(
      @Param("keyword") String keyword,
      @Param("accountStatus") AccountStatus accountStatus,
      @Param("permanentThreshold") LocalDateTime permanentThreshold,
      Pageable pageable);

  @Modifying
  @Query("UPDATE Member m SET m.lastActiveAt = :now WHERE m.memberId = :memberId")
  void updateLastActiveAt(@Param("memberId") UUID memberId, @Param("now") LocalDateTime now);

}
