package com.romrom.member.repository;

import com.romrom.member.entity.Member;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, UUID> {

  Optional<Member> findByEmail(String email);

  Optional<Member> findByEmailAndIsDeletedFalse(String email);

  @Modifying
  @Query("update Member m set m.isDeleted = true, m.accountStatus = 'DELETE_ACCOUNT' where m.memberId = :memberId")
  void deleteByMemberId(UUID memberId);
  
  long countByIsDeletedFalse();
  
  @Query("SELECT COUNT(m) FROM Member m WHERE m.isDeleted = false AND m.accountStatus != 'DELETE_ACCOUNT'")
  long countActiveMembers();
}
