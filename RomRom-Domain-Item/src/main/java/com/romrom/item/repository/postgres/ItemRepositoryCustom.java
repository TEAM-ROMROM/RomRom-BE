package com.romrom.item.repository.postgres;

import com.romrom.common.constant.ItemSortField;
import com.romrom.common.constant.ItemStatus;
import com.romrom.item.entity.postgres.Item;
import com.romrom.member.entity.Member;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ItemRepositoryCustom {

  Page<Item> findAllByMemberAndItemStatusWithMember(
      Member member,
      ItemStatus status,
      Pageable pageable
  );

  Page<Item> filterItemsFetchJoinMember(
      UUID memberId,
      Pageable pageable
  );

  Page<Item> filterItems(
      UUID memberId,
      Double longitude,
      Double latitude,
      Double radius,
      float[] memberEmbedding,
      ItemSortField sortField,
      Pageable pageable
  );
}
