package com.romrom.ai.service;

import com.romrom.web.RomBackApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static me.suhsaechan.suhlogger.util.SuhLogger.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class OllamaAiClientImplTest {

  @Autowired
  @Qualifier("ollamaAiClient")
  AiClient ollamaAiClient;

  @Test
  public void mainTest() {
    lineLog("Ollama 테스트 시작");

    lineLog(null);
    timeLog(this::generateContent);
    timeLog(this::generateEmbedding);
    lineLog(null);

    lineLog("Ollama 테스트 종료");
  }

  @Test
  void generateContent() {
    String testPrompt = "안녕하세요, 반갑습니다!";
    String response = ollamaAiClient.generateContent(testPrompt);
    assertNotNull(response, "텍스트 생성 결과가 null이 아닙니다.");
    superLog("Ollama 텍스트 생성 완료: " + response);
  }

  @Test
  void generateEmbedding() {
    String testText = "테스트 임베딩 텍스트";
    float[] embedding = ollamaAiClient.generateEmbedding(testText);
    assertNotNull(embedding, "임베딩 결과가 null이 아닙니다.");
    assertTrue(embedding.length > 0, "임베딩 차원이 0보다 큽니다.");
    superLog("Ollama 임베딩 완료, 차원: " + embedding.length);
  }
}
