package com.romrom.item.repository.postgres;

import com.romrom.common.constant.ItemCategory;
import com.romrom.item.entity.postgres.UserInteractionScore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInteractionScoreRepository extends JpaRepository<UserInteractionScore, UUID> {
  Optional<UserInteractionScore> findByMemberMemberIdAndItemCategory(UUID memberId, ItemCategory itemCategory);
  List<UserInteractionScore> findByMemberMemberId(UUID memberId);
}
