package com.romrom.report.dto;

import com.romrom.report.entity.ItemReport;
import com.romrom.report.entity.MemberReport;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;
import java.util.Map;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class AdminReportResponse {

  // 목록 조회 시
  @Schema(description = "물품 신고 목록")
  private List<ItemReport> itemReports;

  @Schema(description = "회원 신고 목록")
  private List<MemberReport> memberReports;

  @Schema(description = "전체 페이지 수")
  private Integer totalPages;

  @Schema(description = "전체 요소 수")
  private Long totalElements;

  @Schema(description = "현재 페이지")
  private Integer currentPage;

  // 상세 조회 시
  @Schema(description = "물품 신고 상세")
  private ItemReport itemReport;

  @Schema(description = "회원 신고 상세")
  private MemberReport memberReport;

  // 통계 조회 시
  @Schema(description = "상태별 통계 (item/member 별 상태별 건수)")
  private Map<String, Map<String, Long>> stats;

  // 상태 변경 시
  @Schema(description = "처리 성공 여부")
  private Boolean success;

  @Schema(description = "처리 메시지")
  private String message;
}
