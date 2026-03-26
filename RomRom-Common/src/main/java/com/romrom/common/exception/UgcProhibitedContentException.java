package com.romrom.common.exception;

import lombok.Getter;

@Getter
public class UgcProhibitedContentException extends CustomException {

  private final String violatingText;

  private final String fieldName;

  public UgcProhibitedContentException(String violatingText, String fieldName) {
    super(ErrorCode.PROHIBITED_CONTENT);
    this.violatingText = violatingText;
    this.fieldName = fieldName;
  }
}
