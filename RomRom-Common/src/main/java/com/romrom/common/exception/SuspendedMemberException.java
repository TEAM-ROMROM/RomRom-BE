package com.romrom.common.exception;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class SuspendedMemberException extends CustomException {

  private final String suspendReason;
  private final LocalDateTime suspendedUntil;

  public SuspendedMemberException(String suspendReason, LocalDateTime suspendedUntil) {
    super(ErrorCode.SUSPENDED_MEMBER);
    this.suspendReason = suspendReason;
    this.suspendedUntil = suspendedUntil;
  }
}
