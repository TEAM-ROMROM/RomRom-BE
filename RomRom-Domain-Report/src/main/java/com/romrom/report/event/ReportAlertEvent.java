package com.romrom.report.event;

import com.romrom.report.enums.ReportType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ReportAlertEvent {

  private final ReportType reportType;
  private final UUID targetId;
  private final String targetName;
  private final String reportReasons;
  private final String extraComment;

  @Builder.Default
  private final LocalDateTime reportedAt = LocalDateTime.now();
}
