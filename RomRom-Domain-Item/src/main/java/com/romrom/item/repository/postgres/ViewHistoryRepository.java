package com.romrom.item.repository.postgres;

import com.romrom.item.entity.postgres.ViewHistory;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViewHistoryRepository extends JpaRepository<ViewHistory, UUID> {

  boolean existsByMemberMemberIdAndItemItemIdAndViewedDate(UUID memberId, UUID itemId, LocalDate viewedDate);
}
