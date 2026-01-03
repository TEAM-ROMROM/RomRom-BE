package com.romrom.member.repository;

import com.romrom.member.entity.MemberBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MemberBlockRepository extends JpaRepository<MemberBlock, UUID> {
  boolean existsByBlockerMemberIdAndBlockedMemberId(UUID blockerId, UUID blockedId);

  @Query("SELECT b FROM MemberBlock b JOIN FETCH b.blockedMember WHERE b.blockerMember.memberId = :blockerId")
  List<MemberBlock> findAllByBlockerId(@Param("blockerId") UUID blockerId);

  @Modifying
  @Query("DELETE FROM MemberBlock b WHERE b.blockerMember.memberId = :blockerId AND b.blockedMember.memberId = :blockedId")
  void deleteByBlockerIdAndBlockedId(@Param("blockerId") UUID blockerId, @Param("blockedId") UUID blockedId);
}
