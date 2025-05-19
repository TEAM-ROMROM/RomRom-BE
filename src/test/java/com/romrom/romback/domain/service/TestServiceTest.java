package com.romrom.romback.domain.service;

import static com.romrom.romback.global.util.LogUtil.lineLog;
import static com.romrom.romback.global.util.LogUtil.superLog;
import static com.romrom.romback.global.util.LogUtil.timeLog;

import lombok.extern.slf4j.Slf4j;
import me.suhsaechan.suhnicknamegenerator.service.NicknameGeneratorService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
@Slf4j
class TestServiceTest {

  @Test
  public void mainTest() {
    lineLog("닉네임생성기_테스트");
    timeLog(this::닉네임생성기_테스트);
    lineLog(null);
  }

  public void 닉네임생성기_테스트() {
    NicknameGeneratorService nicknameGeneratorService = new NicknameGeneratorService();
    String string = nicknameGeneratorService.generateNicknameFromName("김민수");
    superLog(string);
  }

}