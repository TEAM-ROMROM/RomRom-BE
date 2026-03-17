package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.entity.MemberReport;
import com.romrom.report.enums.ReportStatus;
import com.romrom.report.enums.ReportType;
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

  @Transactional(readOnly = true)
  public AdminResponse getItemReports(AdminRequest request) {
    Pageable pageable = PageRequest.of(request.getPageNumber(), request.getPageSize());

    Page<ItemReport> page = request.getReportStatus() != null
        ? itemReportRepository.findByStatusOrderByCreatedDateDesc(request.getReportStatus(), pageable)
        : itemReportRepository.findAllByOrderByCreatedDateDesc(pageable);

    return AdminResponse.builder()
        .itemReports(page.getContent())
        .totalPages(page.getTotalPages())
        .totalElements(page.getTotalElements())
        .currentPage(page.getNumber())
        .build();
  }

  @Transactional(readOnly = true)
  public AdminResponse getMemberReports(AdminRequest request) {
    Pageable pageable = PageRequest.of(request.getPageNumber(), request.getPageSize());

    Page<MemberReport> page = request.getReportStatus() != null
        ? memberReportRepository.findByStatusOrderByCreatedDateDesc(request.getReportStatus(), pageable)
        : memberReportRepository.findAllByOrderByCreatedDateDesc(pageable);

    return AdminResponse.builder()
        .memberReports(page.getContent())
        .totalPages(page.getTotalPages())
        .totalElements(page.getTotalElements())
        .currentPage(page.getNumber())
        .build();
  }

  @Transactional(readOnly = true)
  public AdminResponse getItemReportDetail(AdminRequest request) {
    ItemReport itemReport = itemReportRepository.findByItemReportId(request.getReportId())
        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));

    return AdminResponse.builder()
        .itemReport(itemReport)
        .build();
  }

  @Transactional(readOnly = true)
  public AdminResponse getMemberReportDetail(AdminRequest request) {
    MemberReport memberReport = memberReportRepository.findByMemberReportId(request.getReportId())
        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));

    return AdminResponse.builder()
        .memberReport(memberReport)
        .build();
  }

  @Transactional
  public AdminResponse updateStatus(AdminRequest request) {
    if (ReportType.ITEM == request.getReportType()) {
      ItemReport itemReport = itemReportRepository.findByItemReportId(request.getReportId())
          .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
      itemReport.setStatus(request.getNewReportStatus());
      itemReportRepository.save(itemReport);
    } else if (ReportType.MEMBER == request.getReportType()) {
      MemberReport memberReport = memberReportRepository.findByMemberReportId(request.getReportId())
          .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
      memberReport.setStatus(request.getNewReportStatus());
      memberReportRepository.save(memberReport);
    } else {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    return AdminResponse.builder().build();
  }

  @Transactional(readOnly = true)
  public AdminResponse getStats() {
    Map<String, Long> itemStats = new LinkedHashMap<>();
    Map<String, Long> memberStats = new LinkedHashMap<>();

    for (ReportStatus status : ReportStatus.values()) {
      itemStats.put(status.name(), itemReportRepository.countByStatus(status));
      memberStats.put(status.name(), memberReportRepository.countByStatus(status));
    }

    Map<String, Map<String, Long>> stats = new LinkedHashMap<>();
    stats.put("item", itemStats);
    stats.put("member", memberStats);

    return AdminResponse.builder()
        .reportStats(stats)
        .build();
  }
}
