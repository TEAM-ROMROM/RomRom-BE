package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.postgres.Member;
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
}
