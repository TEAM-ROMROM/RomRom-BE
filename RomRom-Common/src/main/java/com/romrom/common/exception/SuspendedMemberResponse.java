package com.romrom.common.exception;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class SuspendedMemberResponse {

  private String errorCode;

  private String suspendReason;

  private LocalDateTime suspendedUntil;
}
