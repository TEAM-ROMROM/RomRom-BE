package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.object.postgres.MemberItemCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MemberItemCategoryRepository extends JpaRepository<MemberItemCategory, UUID> {

  List<MemberItemCategory> findByMemberMemberId(UUID memberId);

  void deleteByMember(Member member);
}
