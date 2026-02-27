package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminItemService {

  private final ItemRepository itemRepository;

  /**
   * 관리자용 물품 목록 조회 (페이지네이션, 필터링, 검색 지원)
   */
  @Transactional(readOnly = true)
  public AdminResponse getItemsForAdmin(AdminRequest request) {
    LocalDateTime startDate = parseDate(request.getStartDate());
    LocalDateTime endDate = parseDate(request.getEndDate());

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(request.getSortDirection(), request.getSortBy())
    );

    log.debug("물품 목록 조회: keyword={}, category={}, condition={}, status={}, page={}, size={}",
        request.getSearchKeyword(), request.getItemCategory(), request.getItemCondition(),
        request.getItemStatus(), request.getPageNumber(), request.getPageSize());

    Page<Item> itemPage = itemRepository.findItemsForAdmin(
        request.getSearchKeyword(),
        request.getItemCategory(),
        request.getItemCondition(),
        request.getItemStatus(),
        request.getMinPrice(),
        request.getMaxPrice(),
        startDate,
        endDate,
        pageable
    );

    log.info("물품 목록 조회 완료: totalElements={}, page={}/{}",
        itemPage.getTotalElements(), itemPage.getNumber(), itemPage.getTotalPages());

    return AdminResponse.builder()
        .items(itemPage)
        .totalCount(itemPage.getTotalElements())
        .build();
  }

  /**
   * 최근 등록 물품 조회 (관리자 대시보드용)
   */
  @Transactional(readOnly = true)
  public AdminResponse getRecentItemsForAdmin(int limit) {
    log.debug("최근 등록 물품 조회: limit={}", limit);
    Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdDate"));
    Page<Item> itemPage = itemRepository.findByIsDeletedFalse(pageable);

    log.info("최근 등록 물품 조회 완료: count={}", itemPage.getContent().size());

    return AdminResponse.builder()
        .items(itemPage)
        .totalCount(itemPage.getTotalElements())
        .build();
  }

  private LocalDateTime parseDate(String dateString) {
    if (dateString == null || dateString.trim().isEmpty()) {
      return null;
    }
    try {
      LocalDate localDate = LocalDate.parse(dateString.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
      return localDate.atStartOfDay();
    } catch (Exception e) {
      log.warn("날짜 파싱 실패: {}", dateString, e);
      return null;
    }
  }
}
