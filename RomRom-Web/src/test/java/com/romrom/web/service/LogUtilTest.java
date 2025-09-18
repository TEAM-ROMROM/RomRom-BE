package com.romrom.web.service;

import static com.romrom.common.dto.deprecated.LogUtil.lineLog;
import static com.romrom.common.dto.deprecated.LogUtil.timeLog;

import com.romrom.common.dto.deprecated.LogUtil;
import lombok.extern.slf4j.Slf4j;
import me.suhsaechan.suhlogger.util.SuhLogger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
@Slf4j
class LogUtilTest {

  @Test
  public void mainTest() {
    lineLog("테스트시작");
//    lineLog("updateIsDownloadedDocumentFiles_동기적");
//    timeLog(this::디버그로그_테스트);
    timeLog(this::suh_logger_테스트);
    lineLog(null);
    lineLog("테스트종료");
  }

  public void 디버그로그_테스트(){
    LogUtil.lineLogDebug("테스트 라인 로그");
    LogUtil.superLogDebug("테스트 객체");
  }

  public void suh_logger_테스트(){
    SuhLogger.lineLog(null);
    SuhLogger.lineLog("테스트 라인 로그");
    SuhLogger.lineLog("성공메시지");
    SuhLogger.lineLog(null);

    SuhLogger.timeLog(this::디버그로그_테스트);
  }
}