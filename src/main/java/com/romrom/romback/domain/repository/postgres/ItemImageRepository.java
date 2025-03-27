package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.postgres.ItemImage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemImageRepository extends JpaRepository<ItemImage, UUID> {

  void deleteByItemItemId(UUID itemId);

  @Modifying
  @Query(value = """
      update romrom.public.item_image
      set deleted = true
      where romrom.public.item_image.item_item_id in
      (select item_id from item where member_member_id = :memberId)
      """, nativeQuery = true)
  void deleteByMemberMemberId(@Param("memberId") UUID memberId);

}
