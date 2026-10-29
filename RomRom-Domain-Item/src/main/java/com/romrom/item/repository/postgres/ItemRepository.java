package com.romrom.item.repository.postgres;

import com.romrom.common.dto.DailyCountProjection;
import com.romrom.common.dto.GroupCountProjection;
import com.romrom.item.entity.postgres.Item;
import com.romrom.member.entity.Member;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

  Page<Item> findAllByMemberAndIsDeletedFalse(Member member, Pageable pageable);

  Page<Item> findAllByMemberMemberIdAndIsDeletedFalse(UUID memberId, Pageable pageable);

  Page<Item> findAllByMemberMemberIdAndIsDeletedFalseAndIsAdminHiddenFalse(UUID memberId, Pageable pageable);

  @Query("select i from Item i left join fetch i.itemImages left join fetch i.member where i.itemId = :itemId")
  Optional<Item> findByItemIdWithImagesAndMember(@Param("itemId") UUID itemId);

  @Query("select i.itemId from Item i " +
         "where i.member = :member " +
         "and i.isDeleted = false " +
         "and i.itemStatus = 'AVAILABLE'")
  List<UUID> findAllAvailableItemIdsByMember(@Param("member") Member member);

  @Query("select i from Item i join fetch i.member where i.itemId in :ids ")
  List<Item> findAllWithMemberByItemIdIn(@Param("ids") List<UUID> ids);

  @Query("select i from Item i join fetch i.member where i.itemId in :ids order by i.createdDate desc")
  List<Item> findAllWithMemberByItemIdInOrderByCreatedDateDesc(@Param("ids") List<UUID> ids);

  @Query("select i from Item i join fetch i.member where i.itemId = :id")
  Optional<Item> findByItemIdWithMember(@Param("id") UUID id);

  Page<Item> findByIsDeletedFalse(Pageable pageable);

  @Query("SELECT i FROM Item i " +
      "JOIN FETCH i.member " +    // N+1 방지위해 작성자 정보 페치 조인
      "WHERE i.itemId IN :itemIds " +
      "AND i.isDeleted = false " +
      "AND i.itemStatus = 'AVAILABLE' " +
      "AND NOT EXISTS (" +
      "    SELECT 1 FROM MemberBlock mb " +
      "    WHERE (mb.blockerMember.memberId = :myId AND mb.blockedMember.memberId = i.member.memberId) " +
      "       OR (mb.blockerMember.memberId = i.member.memberId AND mb.blockedMember.memberId = :myId)" +
      ")")
  List<Item> findByItemIdInAndIsDeletedFalse(@Param("itemIds") List<UUID> itemIds, @Param("myId") UUID myId);

  @Modifying
  @Query("UPDATE Item i SET i.isDeleted = true WHERE i.member.memberId = :memberId")
  void softDeleteAllByMemberId(@Param("memberId") UUID memberId);

  @Query("SELECT i.itemId FROM Item i WHERE i.member.memberId = :memberId AND i.isDeleted = false")
  List<UUID> findAllIdsByMemberId(@Param("memberId") UUID memberId);

  List<Item> findByMemberAndIsDeletedFalseOrderByCreatedDateDesc(Member member);

  Optional<Item> findByItemIdAndIsDeletedFalse(UUID itemId);

  // === Admin 360 View 전용 카운트/페이지 메서드 ===
  Page<Item> findByMemberMemberIdOrderByCreatedDateDesc(UUID memberId, Pageable pageable);

  long countByMemberMemberId(UUID memberId);

  // 관리자 대시보드: soft delete 제외 전체 물품 수
  long countByIsDeletedFalse();

  long countByMemberMemberIdAndIsDeletedFalse(UUID memberId);

  long countByMemberMemberIdAndIsDeletedTrue(UUID memberId);

  long countByMemberMemberIdAndItemStatusAndIsDeletedFalse(UUID memberId, com.romrom.common.constant.ItemStatus itemStatus);

  // ===== 관리자 대시보드 분석 (#827) =====

  /**
   * 날짜별 신규 물품 (등록 시점 기준, 이후 삭제 포함)
   */
  @Query(value = "SELECT TO_CHAR(created_date, 'YYYY-MM-DD') AS day, COUNT(*) AS count FROM item "
      + "WHERE created_date BETWEEN :startDateTime AND :endDateTime GROUP BY TO_CHAR(created_date, 'YYYY-MM-DD')",
      nativeQuery = true)
  List<DailyCountProjection> countDailyNewItems(
      @Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime);

  /**
   * 카테고리 코드별 활성 물품 수 (group_key = ItemCategory 코드 문자열)
   */
  @Query(value = "SELECT CAST(item_category AS varchar) AS group_key, COUNT(*) AS count FROM item "
      + "WHERE is_deleted = false GROUP BY item_category", nativeQuery = true)
  List<GroupCountProjection> countActiveItemsGroupedByCategoryCode();
}
