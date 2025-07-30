package com.romrom.item.repository.postgres;

import com.romrom.item.entity.postgres.Item;
import com.romrom.member.entity.Member;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, UUID> {

  List<Item> findByMemberMemberId(UUID memberId);

  @Modifying
  @Query("update Item i set i.isDeleted = true where i.member.memberId = :memberId")
  void deleteByMemberMemberId(UUID memberId);

  @Modifying
  @Query("update Item i set i.isDeleted = true where i.itemId = :itemId")
  void deleteByItemId(UUID itemId);

  Page<Item> findAllByOrderByCreatedDateDesc(Pageable pageable);

  Page<Item> findAllByMember(Member member, Pageable pageable);

  List<Item> findAllByMember(Member member);

  @Query(value = "SELECT * FROM item WHERE member_member_id != :memberId AND is_deleted = false",
      countQuery = "SELECT COUNT(*) FROM item WHERE member_member_id != :memberId AND is_deleted = false",
      nativeQuery = true)
  Page<Item> filterItems(@Param("memberId") UUID memberId, Pageable pageable);
}
