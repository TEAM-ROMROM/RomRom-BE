package com.romrom.report.dto;

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
public class ItemReportRequest {

  @Schema(description = "회원")
  private Member member;

  @Schema(description = "물품 ID")
  private UUID itemId;

  @Schema(description = "신고 사유 코드들", required = true,
      example = "[\"FRAUD\", \"INAPPROPRIATE\"]")
  private Set<ItemReportReason> reasons;

  @Schema(description = "기타 입력 내용")
  private String extraComment;
}
