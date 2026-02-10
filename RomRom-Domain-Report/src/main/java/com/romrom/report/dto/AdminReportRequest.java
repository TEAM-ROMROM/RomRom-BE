package com.romrom.report.dto;

import com.romrom.report.enums.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class AdminReportRequest {

  @Schema(description = "API 액션 타입 (item-list, member-list, item-detail, member-detail, update-status, stats)")
  private String action;

  @Schema(description = "신고 ID (상세 조회, 상태 변경 시 사용)")
  private UUID reportId;

  @Schema(description = "신고 유형 (ITEM / MEMBER, 상태 변경 시 사용)")
  private String type;

  @Schema(description = "변경할 상태 (상태 변경 시 사용)")
  private ReportStatus newStatus;

  @Schema(description = "상태 필터 (목록 조회 시 사용)")
  private ReportStatus status;

  @Schema(description = "페이지 번호", defaultValue = "0")
  @Builder.Default
  private Integer page = 0;

  @Schema(description = "페이지 크기", defaultValue = "20")
  @Builder.Default
  private Integer size = 20;

  @Schema(description = "정렬 필드", defaultValue = "createdDate")
  @Builder.Default
  private String sortBy = "createdDate";

  @Schema(description = "정렬 방향", defaultValue = "DESC")
  @Builder.Default
  private Sort.Direction sortDirection = Sort.Direction.DESC;
}
