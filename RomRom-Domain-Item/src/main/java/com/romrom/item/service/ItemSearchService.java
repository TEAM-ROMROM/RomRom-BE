package com.romrom.item.service;

import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemSearchSortField;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import com.romrom.item.dto.ItemSearchCondition;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberLocationRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 물품 검색: 검색어 정리·검증 > 정렬·거리 기준 좌표 확정 > 노출 조건 적용 조회 (#862)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemSearchService {

  // 검색어 최대 길이 (초과분 잘라냄, LIKE 비용 상한)
  static final int MAXIMUM_SEARCH_KEYWORD_LENGTH = 50;
  // 한 페이지 최대 물품 수
  static final int MAXIMUM_PAGE_SIZE = 50;

  private final ItemRepository itemRepository;
  private final MemberLocationRepository memberLocationRepository;

  /**
   * 물품 검색 (excludedItemIds: 조회 회원이 신고한 물품 등 결과에서 뺄 물품)
   */
  @Transactional(readOnly = true)
  public ItemResponse searchItems(ItemRequest request, Collection<UUID> excludedItemIds) {
    UUID viewerMemberId = request.getMember().getMemberId();
    String keyword = normalizeSearchKeyword(request.getSearchKeyword());
    ItemSearchSortField sortField = Optional.ofNullable(request.getSearchSortField()).orElse(ItemSearchSortField.LATEST);

    // 거리순만 회원 위치 사용 (위치 미등록 > 최신순 정렬)
    Optional<MemberLocation> viewerLocation = sortField == ItemSearchSortField.DISTANCE
        ? memberLocationRepository.findByMemberMemberId(viewerMemberId)
        : Optional.empty();

    ItemSearchCondition searchCondition = new ItemSearchCondition(
        viewerMemberId,
        keyword,
        toDistinctCategories(request.getSearchItemCategories()),
        sortField,
        viewerLocation.map(MemberLocation::getLongitude).orElse(null),
        viewerLocation.map(MemberLocation::getLatitude).orElse(null),
        excludedItemIds
    );

    Page<Item> itemPage = itemRepository.searchVisibleItems(searchCondition, toPageable(request));
    return ItemResponse.builder()
        .itemPage(itemPage)
        .build();
  }

  /**
   * 검색어 정리: 앞뒤 공백 제거 > 1자 미만 거부 > 최대 길이 초과분 잘라냄
   */
  static String normalizeSearchKeyword(String searchKeyword) {
    String trimmedKeyword = searchKeyword == null ? "" : searchKeyword.trim();
    if (trimmedKeyword.isEmpty()) {
      log.warn("[item search] rejected 400 : keyword is empty");
      throw new CustomException(ErrorCode.INVALID_SEARCH_KEYWORD);
    }
    return trimmedKeyword.length() > MAXIMUM_SEARCH_KEYWORD_LENGTH
        ? trimmedKeyword.substring(0, MAXIMUM_SEARCH_KEYWORD_LENGTH)
        : trimmedKeyword;
  }

  /**
   * 카테고리 필터 정리 (null·중복 제거, 미전송 > 빈 목록 = 전체)
   */
  private static List<ItemCategory> toDistinctCategories(List<ItemCategory> requestedCategories) {
    if (requestedCategories == null) {
      return List.of();
    }
    return requestedCategories.stream().filter(Objects::nonNull).distinct().toList();
  }

  /**
   * 페이지 요청 (번호 0 미만 > 0, 크기 1~최대값으로 보정)
   */
  private static Pageable toPageable(ItemRequest request) {
    int pageNumber = Math.max(0, request.getPageNumber());
    int pageSize = Math.min(Math.max(1, request.getPageSize()), MAXIMUM_PAGE_SIZE);
    return PageRequest.of(pageNumber, pageSize);
  }
}
