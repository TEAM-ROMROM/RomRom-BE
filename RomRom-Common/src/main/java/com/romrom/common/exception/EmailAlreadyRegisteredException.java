package com.romrom.common.exception;

import com.romrom.common.constant.SocialPlatform;
import lombok.Getter;

@Getter
public class EmailAlreadyRegisteredException extends CustomException {

  private final SocialPlatform registeredSocialPlatform;

  public EmailAlreadyRegisteredException(SocialPlatform registeredSocialPlatform) {
    super(ErrorCode.EMAIL_ALREADY_REGISTERED);
    this.registeredSocialPlatform = registeredSocialPlatform;
  }
}
