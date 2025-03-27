package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.postgres.MemberItemCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberProductCategoryRepository extends JpaRepository<MemberItemCategory, UUID> {

  List<MemberItemCategory> findByMemberMemberId(UUID memberId);

  void deleteByMemberMemberId(UUID memberId);
}
