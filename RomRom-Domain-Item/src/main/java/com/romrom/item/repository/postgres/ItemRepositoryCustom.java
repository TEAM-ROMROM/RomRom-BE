package com.romrom.item.repository.postgres;

import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemSortField;
import com.romrom.common.constant.ItemStatus;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.UserInteractionScore;
import com.romrom.member.entity.Member;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ItemRepositoryCustom {

  Page<Item> findAllByMemberAndItemStatusAndIsDeletedFalseWithMember(
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
      Double radiusInMeters,
      float[] memberEmbedding,
      List<UserInteractionScore> userInteractionScores,
      List<ItemCategory> preferredCategories,
      ItemSortField sortField,
      Pageable pageable
  );

  Page<Item> findItemsForAdmin(
      String searchKeyword,
      ItemCategory itemCategory,
      ItemCondition itemCondition,
      ItemStatus itemStatus,
      Integer minPrice,
      Integer maxPrice,
      LocalDateTime startDate,
      LocalDateTime endDate,
      Pageable pageable
  );
}
