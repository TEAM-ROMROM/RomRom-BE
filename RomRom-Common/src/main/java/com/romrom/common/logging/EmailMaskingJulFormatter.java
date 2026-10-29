package com.romrom.common.logging;

import com.romrom.common.util.LogMaskUtil;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * JUL Formatter 감싸기: 원래 형식 그대로 출력하되 이메일만 마스킹 (#825)
 * suhlogger는 logback을 거치지 않고 자체 JUL 핸들러로 콘솔에 직접 출력 > logback 변환기로는 못 막음
 */
public class EmailMaskingJulFormatter extends Formatter {

  private final Formatter delegateFormatter;

  public EmailMaskingJulFormatter(Formatter delegateFormatter) {
    this.delegateFormatter = delegateFormatter;
  }

  @Override
  public String format(LogRecord logRecord) {
    String formattedRecord = delegateFormatter != null ? delegateFormatter.format(logRecord) : formatMessage(logRecord);
    return LogMaskUtil.maskEmailsInText(formattedRecord);
  }

  @Override
  public String getHead(Handler handler) {
    return delegateFormatter != null ? delegateFormatter.getHead(handler) : super.getHead(handler);
  }

  @Override
  public String getTail(Handler handler) {
    return delegateFormatter != null ? delegateFormatter.getTail(handler) : super.getTail(handler);
  }
}
