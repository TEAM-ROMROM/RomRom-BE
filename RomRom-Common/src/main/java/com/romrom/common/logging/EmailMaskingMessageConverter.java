package com.romrom.common.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.romrom.common.util.LogMaskUtil;

/**
 * logback %m·%msg 대체: 출력 메시지 속 이메일 마스킹 (#825)
 * 응답 본문 로그(suh-logger)처럼 코드에서 막을 수 없는 출력까지 콘솔·파일 공통으로 적용, 등록은 logback-spring.xml
 */
public class EmailMaskingMessageConverter extends MessageConverter {

  @Override
  public String convert(ILoggingEvent loggingEvent) {
    return LogMaskUtil.maskEmailsInText(super.convert(loggingEvent));
  }
}
