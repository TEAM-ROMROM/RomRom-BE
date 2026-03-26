package com.romrom.common.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class UgcViolationResponse {

  private String errorCode;

  private String errorMessage;

  private String violatingText;

  private String fieldName;
}
