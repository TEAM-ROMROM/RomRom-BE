package com.romrom.application.dto;

import com.romrom.common.entity.postgres.AdminAuditLog;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 관리자 작업 기록 목록 응답 (#874): 기록 목록(최근순), 페이지 정보
 */
@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class AdminAuditLogResponse {

  @Schema(description = "작업 기록 목록 (최근순, requestSummary 는 JSON 문자열)")
  private List<AdminAuditLog> adminAuditLogs;

  @Schema(description = "전체 페이지 수")
  private Integer totalPages;

  @Schema(description = "전체 요소 수")
  private Long totalElements;

  @Schema(description = "현재 페이지")
  private Integer currentPage;
}
