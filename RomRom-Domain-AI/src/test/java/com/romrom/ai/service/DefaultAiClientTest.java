package com.romrom.ai.service;

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
class DefaultAiClientTest {

  @Autowired
  AiClient defaultAiClient;  // @Primary로 DefaultAiClient가 주입됨

  @Test
  public void mainTest() {
    lineLog("Default AI Client 테스트 시작");

    lineLog(null);
    timeLog(this::generateContent);
    timeLog(this::generateEmbedding);
    timeLog(this::getItemPricePrediction);
    lineLog(null);

    lineLog("Default AI Client 테스트 종료");
  }

  @Test
  void generateContent() {
    String testPrompt = "안녕하세요, 반갑습니다!";
    String response = defaultAiClient.generateContent(testPrompt);
    assertNotNull(response, "텍스트 생성 결과가 null이 아닙니다.");
    superLog("[Default] 텍스트 생성 완료: " + response);
  }

  @Test
  void generateEmbedding() {
    String testText = "테스트 임베딩 텍스트";
    float[] embedding = defaultAiClient.generateEmbedding(testText);
    assertNotNull(embedding, "임베딩 결과가 null이 아닙니다.");
    assertTrue(embedding.length > 0, "임베딩 차원이 0보다 큽니다.");
    superLog("[Default] 임베딩 완료, 차원: " + embedding.length);
  }

  @Test
  void getItemPricePrediction() {
    String testItem = """
        ITEM_NAME: 아이폰 14 프로 맥스 256GB
        ITEM_DESCRIPTION: 구매한지 1년됨, 기스 약간 있음, 풀박스
        ITEM_CONDITION: 약간 사용감
        """;
    int price = defaultAiClient.getItemPricePrediction(testItem);
    assertTrue(price > 0, "가격 예측 결과가 0보다 큽니다.");
    superLog("[Default] 가격 예측 완료, 예측 가격: " + price + "원");
  }

  @Test
  void testClientName() {
    String clientName = defaultAiClient.getClientName();
    assertEquals("Default(Ollama→VertexAI)", clientName);
    superLog("[Default] 클라이언트 이름: " + clientName);
  }
}
