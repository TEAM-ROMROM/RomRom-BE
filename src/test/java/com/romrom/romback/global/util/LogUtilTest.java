package com.romrom.romback.global.util;

import static com.romrom.romback.global.util.LogUtil.lineLog;
import static com.romrom.romback.global.util.LogUtil.timeLog;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
@Slf4j
class LogUtilTest {

  @Test
  public void mainTest() {
    lineLog("updateIsDownloadedDocumentFiles_동기적");
    timeLog(this::디버그로그_테스트);
    lineLog(null);
  }

  public void 디버그로그_테스트(){
    LogUtil.lineLogDebug("테스트 라인 로그");
    LogUtil.superLogDebug("테스트 객체");
  }
}