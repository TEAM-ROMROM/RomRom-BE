package com.romrom.application.dto;

import com.romrom.report.entity.ChatMessageReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 관리자 채팅 메시지 신고 응답 (#873): 목록(페이지 정보 포함), 상세 + 처리용 요약
 * AdminResponse 를 키우지 않기 위한 분리 DTO
 */
@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class AdminChatMessageReportResponse {

  @Schema(description = "채팅 메시지 신고 목록 (접수 최신순, 목록 조회용)")
  private List<ChatMessageReport> chatMessageReports;

  @Schema(description = "채팅 메시지 신고 상세 (상세 조회용)")
  private ChatMessageReport chatMessageReport;

  @Schema(description = "신고 원스톱 처리용 요약 (상세 조회용, reportType=CHAT_MESSAGE)")
  private AdminResponse.ReportResolveDetail reportResolveDetail;

  @Schema(description = "전체 페이지 수")
  private Integer totalPages;

  @Schema(description = "전체 요소 수")
  private Long totalElements;

  @Schema(description = "현재 페이지")
  private Integer currentPage;
}
