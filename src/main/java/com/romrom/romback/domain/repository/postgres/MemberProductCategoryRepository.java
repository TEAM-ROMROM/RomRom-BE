package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.object.postgres.MemberProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MemberProductCategoryRepository extends JpaRepository<MemberProductCategory, UUID> {

  List<MemberProductCategory> findByMemberMemberId(UUID memberId);

  void deleteByMember(Member member);
}
