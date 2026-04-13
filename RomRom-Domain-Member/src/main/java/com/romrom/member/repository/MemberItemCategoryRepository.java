package com.romrom.member.repository;

import com.romrom.member.entity.MemberItemCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MemberItemCategoryRepository extends JpaRepository<MemberItemCategory, UUID> {

  List<MemberItemCategory> findByMemberMemberId(UUID memberId);

  @Modifying
  @Query("DELETE FROM MemberItemCategory m WHERE m.member.memberId = :memberId")
  void deleteByMemberMemberId(UUID memberId);
}
