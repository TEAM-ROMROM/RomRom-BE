package com.romrom.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "관리자 일괄 작업 개별 결과")
public class BulkActionResult {

  @Schema(description = "대상 ID (itemId 등)")
  private UUID targetId;

  @Schema(description = "성공 여부")
  private Boolean isSuccess;

  @Schema(description = "실패 사유 (실패 시에만)")
  private String failReason;
}
