package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.postgres.Item;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, UUID> {

  Boolean existsByMember_MemberId(UUID memberId);
}
