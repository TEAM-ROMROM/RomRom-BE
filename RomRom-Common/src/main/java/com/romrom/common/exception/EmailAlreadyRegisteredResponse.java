package com.romrom.common.exception;

import com.romrom.common.constant.SocialPlatform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class EmailAlreadyRegisteredResponse {

  private String errorCode;

  private SocialPlatform registeredSocialPlatform;
}
