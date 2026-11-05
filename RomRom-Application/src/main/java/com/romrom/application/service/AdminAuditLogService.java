package com.romrom.application.service;

import com.romrom.application.dto.AdminAuditLogRequest;
import com.romrom.application.dto.AdminAuditLogResponse;
import com.romrom.application.util.AdminDateRangeParser;
import com.romrom.common.entity.postgres.AdminAuditLog;
import com.romrom.common.repository.AdminAuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 관리자 작업 기록 (#874): 기록 저장(별도 트랜잭션), 목록 조회(기간·관리자·작업·대상 필터, 최근순 페이지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

  // 목록 페이지 크기 상한
  private static final int MAXIMUM_PAGE_SIZE = 100;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final String CREATED_DATE_PROPERTY = "createdDate";

  private final AdminAuditLogRepository adminAuditLogRepository;

  /**
   * 기록 1행 저장 (REQUIRES_NEW: 업무 트랜잭션과 분리, 기록 실패가 업무 롤백으로 번지지 않음)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AdminAuditLog adminAuditLog) {
    adminAuditLogRepository.save(adminAuditLog);
  }

  /**
   * 목록: 조건 > 최근순 페이지 1회 조회
   */
  @Transactional(readOnly = true)
  public AdminAuditLogResponse getAuditLogs(AdminAuditLogRequest request) {
    int pageNumber = request.getPageNumber() != null && request.getPageNumber() >= 0 ? request.getPageNumber() : 0;
    int pageSize = request.getPageSize() != null && request.getPageSize() > 0
        ? Math.min(request.getPageSize(), MAXIMUM_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
    PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, CREATED_DATE_PROPERTY));

    Page<AdminAuditLog> adminAuditLogPage = adminAuditLogRepository.findAll(buildSearchSpecification(request), pageRequest);
    return AdminAuditLogResponse.builder()
        .adminAuditLogs(adminAuditLogPage.getContent())
        .totalPages(adminAuditLogPage.getTotalPages())
        .totalElements(adminAuditLogPage.getTotalElements())
        .currentPage(adminAuditLogPage.getNumber())
        .build();
  }

  // 조건 5개 (입력한 것만): 기간, 관리자(ID 또는 이메일 일부), 작업 종류, 대상 종류, 대상 ID
  private static Specification<AdminAuditLog> buildSearchSpecification(AdminAuditLogRequest request) {
    LocalDateTime startDateTime = AdminDateRangeParser.parseStartOfDay(request.getStartDate());
    LocalDateTime endDateTime = AdminDateRangeParser.parseEndOfDay(request.getEndDate());
    String adminKeyword = StringUtils.hasText(request.getAdminKeyword()) ? request.getAdminKeyword().trim() : null;
    UUID adminMemberId = adminKeyword != null ? parseUuidOrNull(adminKeyword) : null;
    String targetId = StringUtils.hasText(request.getTargetId()) ? request.getTargetId().trim() : null;

    return (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (startDateTime != null) {
        predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(CREATED_DATE_PROPERTY), startDateTime));
      }
      if (endDateTime != null) {
        predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(CREATED_DATE_PROPERTY), endDateTime));
      }
      if (adminMemberId != null) {
        predicates.add(criteriaBuilder.equal(root.get("adminMemberId"), adminMemberId));
      } else if (adminKeyword != null) {
        predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("adminEmail")),
            "%" + adminKeyword.toLowerCase(Locale.ROOT) + "%"));
      }
      if (request.getAdminAuditAction() != null) {
        predicates.add(criteriaBuilder.equal(root.get("action"), request.getAdminAuditAction()));
      }
      if (request.getAdminAuditTargetType() != null) {
        predicates.add(criteriaBuilder.equal(root.get("targetType"), request.getAdminAuditTargetType()));
      }
      if (targetId != null) {
        predicates.add(criteriaBuilder.equal(root.get("targetId"), targetId));
      }
      return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static UUID parseUuidOrNull(String adminKeyword) {
    try {
      return UUID.fromString(adminKeyword);
    } catch (IllegalArgumentException notUuidException) {
      return null;
    }
  }
}
