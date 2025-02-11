package com.romrom.romback.global.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class OAuthConfig {

  @Value("${oauth.kakao.userinfo-url}")
  private String kakaoUserInfoUrl;

  @Value("${oauth.google.userinfo-url}")
  private String googleUserInfoUrl;
}
