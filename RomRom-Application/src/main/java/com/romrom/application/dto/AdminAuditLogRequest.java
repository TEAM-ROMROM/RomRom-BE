package com.romrom.application.dto;

import com.romrom.common.constant.AdminAuditAction;
import com.romrom.common.constant.AdminAuditTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 관리자 작업 기록 목록 요청 (#874, multipart/form-data): 기간, 관리자, 작업 종류, 대상, 페이지
 */
@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class AdminAuditLogRequest {

  @Schema(description = "시작일 (yyyy-MM-dd, 포함)")
  private String startDate;

  @Schema(description = "종료일 (yyyy-MM-dd, 포함)")
  private String endDate;

  @Schema(description = "관리자 이메일 일부 또는 관리자 회원 ID(UUID)")
  private String adminKeyword;

  @Schema(description = "작업 종류")
  private AdminAuditAction adminAuditAction;

  @Schema(description = "대상 종류")
  private AdminAuditTargetType adminAuditTargetType;

  @Schema(description = "대상 ID (정확히 일치)")
  private String targetId;

  @Schema(description = "페이지 번호", defaultValue = "0")
  @Builder.Default
  private Integer pageNumber = 0;

  @Schema(description = "페이지 크기 (최대 100)", defaultValue = "20")
  @Builder.Default
  private Integer pageSize = 20;
}
