package com.romrom.mail.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "alert")
public class AlertProperties {

  // 같은 신고 대상 알림 재발송 간격 기본값 (분, 시스템 설정 미입력·잘못된 값일 때)
  public static final int DEFAULT_THROTTLE_MINUTES = 30;
  private String email;
  private int throttleMinutes;
}
