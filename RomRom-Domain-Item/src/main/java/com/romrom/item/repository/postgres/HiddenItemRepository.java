package com.romrom.item.repository.postgres;

import com.romrom.item.entity.postgres.HiddenItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HiddenItemRepository extends JpaRepository<HiddenItem, UUID> {

  boolean existsByMemberMemberIdAndItemItemId(UUID memberId, UUID itemId);

  void deleteByMemberMemberIdAndItemItemId(UUID memberId, UUID itemId);

  void deleteAllByMemberMemberId(UUID memberId);

  void deleteAllByItemItemId(UUID itemId);
}
