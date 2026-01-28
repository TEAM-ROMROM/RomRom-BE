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

public interface ItemRepository extends JpaRepository<Item, UUID>, ItemRepositoryCustom {

  List<Item> findByMemberMemberId(UUID memberId);

  @Modifying
  @Query("update Item i set i.isDeleted = true where i.member.memberId = :memberId")
  void deleteByMemberMemberId(UUID memberId);

  @Modifying
  @Query("update Item i set i.isDeleted = true where i.itemId = :itemId")
  void deleteByItemId(UUID itemId);

  Page<Item> findAllByMember(Member member, Pageable pageable);

  @Query("select i.itemId from Item i where i.member = :member and i.isDeleted = false")
  List<UUID> findAllItemIdsByMember(@Param("member") Member member);

  @Query("select i from Item i join fetch i.member where i.itemId in :ids ")
  List<Item> findAllWithMemberByItemIdIn(@Param("ids") List<UUID> ids);

  Page<Item> findByIsDeletedFalse(Pageable pageable);

  @Query("SELECT i FROM Item i " +
      "JOIN FETCH i.member " +    // N+1 방지위해 작성자 정보 페치 조인
      "WHERE i.itemId IN :itemIds " +
      "AND NOT EXISTS (" +
      "    SELECT 1 FROM MemberBlock mb " +
      "    WHERE (mb.blockerMember.memberId = :myId AND mb.blockedMember.memberId = i.member.memberId) " +
      "       OR (mb.blockerMember.memberId = i.member.memberId AND mb.blockedMember.memberId = :myId)" +
      ")")
  List<Item> findByItemIdIn(@Param("itemIds") List<UUID> itemIds, @Param("myId") UUID myId);
}
