package com.romrom.ai.service;

import com.romrom.ai.EmbeddingUtil;
import com.romrom.web.RomBackApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static me.suhsaechan.suhlogger.util.SuhLogger.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class VertexAiClientImplTest {

  @Autowired
  VertexAiClientImpl vertexAiClientImpl;

  @Test
  public void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::generateEmbedding);
    timeLog(this::getItemPricePrediction);
    lineLog(null);

    lineLog("테스트종료");
  }

  @Test
  void generateEmbedding() {
    String testQuery = "테스트 쿼리";
    // 임베딩 생성 결과를 검증하는 로직을 추가할 수 있습니다.
    assertNotNull(vertexAiClientImpl.generateEmbedding(testQuery), "임베딩 생성 결과가 null이 아닙니다.");
  }

  @Test
  void getItemPricePrediction() {
    String testQuery = "테스트 쿼리";
    int value = vertexAiClientImpl.getItemPricePrediction(testQuery);
    // 가격 예측 결과를 검증하는 로직을 추가할 수 있습니다.
    assertTrue(value > 0, "가격 예측 결과가 0보다 큽니다.");
    superLog("가격 예측 완료, 예측 가격 : " + value);
  }
}