package com.romrom.item.repository.postgres;

import com.romrom.common.constant.ItemStatus;
import com.romrom.item.entity.postgres.Item;
import com.romrom.member.entity.Member;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, UUID>, ItemRepositoryCustom {

  List<Item> findByMemberMemberId(UUID memberId);

  @Modifying
  @Query("update Item i set i.isDeleted = true where i.member.memberId = :memberId")
  void deleteByMemberMemberId(UUID memberId);

  @Modifying
  @Query("update Item i set i.isDeleted = true where i.itemId = :itemId")
  void deleteByItemId(UUID itemId);

  Page<Item> findAllByMember(Member member, Pageable pageable);

  Page<Item> findAllByMemberAndItemStatus(Member member, ItemStatus status, Pageable pageable);

  @Query("select i.itemId from Item i where i.member = :member and i.isDeleted = false")
  List<UUID> findAllItemIdsByMember(@Param("member") Member member);

  List<Item> findAllByItemIdIn(List<UUID> itemIds);

  @Query(
      value = "SELECT i FROM Item i JOIN FETCH i.member " +
          "WHERE i.member = :member AND i.itemStatus = :status",
      countQuery = "SELECT COUNT(i) FROM Item i " +
          "WHERE i.member = :member AND i.itemStatus = :status"
  )
  Page<Item> findAllByMemberAndItemStatusWithMember(
      @Param("member") Member member,
      @Param("status") ItemStatus status,
      Pageable pageable
  );

  @Query(value = """
    select i from Item i join fetch i.member m where i.isDeleted = false and m.memberId <> :memberId
  """,
      countQuery = """
    select count(i) from Item i where i.isDeleted = false and i.member.memberId <> :memberId
  """
  )
  Page<Item> filterItemsFetchJoinMember(@Param("memberId") UUID memberId, Pageable pageable);
  List<Item> findAllByMember(Member member);
}
