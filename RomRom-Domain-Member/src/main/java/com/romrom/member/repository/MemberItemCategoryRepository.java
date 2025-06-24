package com.romrom.member.repository;

import com.romrom.member.entity.MemberItemCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberItemCategoryRepository extends JpaRepository<MemberItemCategory, UUID> {

  List<MemberItemCategory> findByMemberMemberId(UUID memberId);

  void deleteByMemberMemberId(UUID memberId);
}
