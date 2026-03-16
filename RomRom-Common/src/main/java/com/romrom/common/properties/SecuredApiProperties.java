package com.romrom.common.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SecuredApi 서명 검증 설정
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "secured-api")
public class SecuredApiProperties {

  /**
   * HMAC 서명용 Secret Key
   */
  private String secretKey;

  /**
   * 타임스탬프 만료 시간 (밀리초, 기본값: 5분)
   */
  private long expirationTime = 300000L;
}
