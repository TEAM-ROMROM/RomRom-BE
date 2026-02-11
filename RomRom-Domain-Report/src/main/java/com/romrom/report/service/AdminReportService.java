package com.romrom.report.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.report.dto.AdminReportRequest;
import com.romrom.report.dto.AdminReportResponse;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.entity.MemberReport;
import com.romrom.report.enums.ReportStatus;
import com.romrom.report.repository.ItemReportRepository;
import com.romrom.report.repository.MemberReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReportService {

  private final ItemReportRepository itemReportRepository;
  private final MemberReportRepository memberReportRepository;

  @Transactional
  public AdminReportResponse handleAction(AdminReportRequest request) {
    return switch (request.getAction()) {
      case "item-list" -> getItemReports(request);
      case "member-list" -> getMemberReports(request);
      case "item-detail" -> getItemReportDetail(request);
      case "member-detail" -> getMemberReportDetail(request);
      case "update-status" -> updateStatus(request);
      case "stats" -> getStats();
      default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
    };
  }

  private AdminReportResponse getItemReports(AdminReportRequest request) {
    Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

    Page<ItemReport> page = request.getStatus() != null
        ? itemReportRepository.findByStatusOrderByCreatedDateDesc(request.getStatus(), pageable)
        : itemReportRepository.findAllByOrderByCreatedDateDesc(pageable);

    return AdminReportResponse.builder()
        .itemReports(page.getContent())
        .totalPages(page.getTotalPages())
        .totalElements(page.getTotalElements())
        .currentPage(page.getNumber())
        .build();
  }

  private AdminReportResponse getMemberReports(AdminReportRequest request) {
    Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

    Page<MemberReport> page = request.getStatus() != null
        ? memberReportRepository.findByStatusOrderByCreatedDateDesc(request.getStatus(), pageable)
        : memberReportRepository.findAllByOrderByCreatedDateDesc(pageable);

    return AdminReportResponse.builder()
        .memberReports(page.getContent())
        .totalPages(page.getTotalPages())
        .totalElements(page.getTotalElements())
        .currentPage(page.getNumber())
        .build();
  }

  private AdminReportResponse getItemReportDetail(AdminReportRequest request) {
    ItemReport itemReport = itemReportRepository.findByItemReportId(request.getReportId())
        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));

    return AdminReportResponse.builder()
        .itemReport(itemReport)
        .build();
  }

  private AdminReportResponse getMemberReportDetail(AdminReportRequest request) {
    MemberReport memberReport = memberReportRepository.findByMemberReportId(request.getReportId())
        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));

    return AdminReportResponse.builder()
        .memberReport(memberReport)
        .build();
  }

  @Transactional
  public AdminReportResponse updateStatus(AdminReportRequest request) {
    if ("ITEM".equals(request.getType())) {
      ItemReport itemReport = itemReportRepository.findByItemReportId(request.getReportId())
          .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
      itemReport.setStatus(request.getNewStatus());
      itemReportRepository.save(itemReport);
    } else if ("MEMBER".equals(request.getType())) {
      MemberReport memberReport = memberReportRepository.findByMemberReportId(request.getReportId())
          .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
      memberReport.setStatus(request.getNewStatus());
      memberReportRepository.save(memberReport);
    } else {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    return AdminReportResponse.builder()
        .success(true)
        .message("상태가 변경되었습니다.")
        .build();
  }

  private AdminReportResponse getStats() {
    Map<String, Long> itemStats = new LinkedHashMap<>();
    Map<String, Long> memberStats = new LinkedHashMap<>();

    for (ReportStatus status : ReportStatus.values()) {
      itemStats.put(status.name(), itemReportRepository.countByStatus(status));
      memberStats.put(status.name(), memberReportRepository.countByStatus(status));
    }

    Map<String, Map<String, Long>> stats = new LinkedHashMap<>();
    stats.put("item", itemStats);
    stats.put("member", memberStats);

    return AdminReportResponse.builder()
        .stats(stats)
        .build();
  }
}
