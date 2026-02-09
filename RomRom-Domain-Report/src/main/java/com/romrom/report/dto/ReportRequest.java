package com.romrom.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.romrom.member.entity.Member;
import com.romrom.report.enums.ItemReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.Set;
import java.util.UUID;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ReportRequest {

  @Schema(hidden = true, description = "회원")
  @JsonIgnore
  private Member member;

  @Schema(description = "신고할 물품 ID")
  private UUID itemId;

  @Schema(description = "물품 신고 사유 코드들")
  private Set<Integer> itemReportReasons;

  @Schema(description = "신고할 회원 ID")
  private UUID targetMemberId;

  @Schema(description = "회원 신고 사유 코드들")
  private Set<Integer> memberReportReasons;

  @Schema(description = "기타 입력 내용")
  private String extraComment;
}
