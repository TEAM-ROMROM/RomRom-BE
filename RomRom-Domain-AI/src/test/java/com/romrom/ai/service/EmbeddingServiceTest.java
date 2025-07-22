package com.romrom.ai.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.superLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.romrom.web.RomBackApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class EmbeddingServiceTest {

  @Autowired
  EmbeddingService embeddingService;

  @Test
  public void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::generateDummyEmbedding_TEST);
    lineLog(null);

    lineLog("테스트종료");
  }

  public void generateDummyEmbedding_TEST(){
//    log.debug("더미 임베딩 생성 요청: {}", text);
//
//    // 임시 더미 임베딩 (실제 구현시 AI 서비스 호출로 대체)
//    float[] embedding = new float[384];
//    for (int i = 0; i < 384; i++) {
//      embedding[i] = (float) Math.random();
//    }
//
//    log.debug("더미 임베딩 생성 완료: 차원={}", embedding.length);
//    return embedding;
    String testQuery = "테스트 쿼리";
    float[] generateDummyEmbeddingFloats = embeddingService.generateDummyEmbedding(testQuery);
    superLog(generateDummyEmbeddingFloats);
  }
}