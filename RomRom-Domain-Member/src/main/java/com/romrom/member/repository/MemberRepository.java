package com.romrom.member.repository;

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

  @Modifying
  @Query("UPDATE Member m SET m.lastActiveAt = :now WHERE m.memberId = :memberId")
  void updateLastActiveAt(@Param("memberId") UUID memberId, @Param("now") LocalDateTime now);
}
