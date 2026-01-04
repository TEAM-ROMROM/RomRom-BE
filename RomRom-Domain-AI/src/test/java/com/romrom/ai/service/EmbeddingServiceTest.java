package com.romrom.ai.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.superLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.romrom.ai.properties.SuhAiderProperties;
import com.romrom.common.util.CommonUtil;
import com.romrom.web.RomBackApplication;
import java.util.List;
import java.util.UUID;
import kr.suhsaechan.ai.service.SuhAiderEngine;
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

  @Autowired
  SuhAiderEngine suhAiderEngine;

  @Autowired
  SuhAiderProperties suhAiderProperties;

  @Test
  public void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::suhAiderEmbedding_TEST);
//    timeLog(this::generateItemEmbedding_TEST);
    lineLog(null);

    lineLog("테스트종료");
  }

  /**
   * SUH-AIder 임베딩 테스트
   * embeddinggemma:latest 모델로 768차원 벡터 생성 확인
   */
  public void suhAiderEmbedding_TEST() {
    String testText = "맥북 프로 16인치 M3 Pro 실버 색상 팔아요";
    String embeddingModel = suhAiderProperties.getEmbedding().getDefaultModel();

    lineLog("SUH-AIder 임베딩 테스트");
    lineLog("테스트 텍스트" + testText);
    lineLog("임베딩 모델" + embeddingModel);

    // SUH-AIder 임베딩 생성
    List<Double> embedding = suhAiderEngine.embed(embeddingModel, testText);
    float[] embeddingVector = CommonUtil.convertDoubleListToFloatArray(embedding);

    lineLog("임베딩 차원" + embeddingVector.length);
    lineLog("임베딩 벡터 (처음 10개)" + getFirst10Elements(embeddingVector));

    // 차원 검증 (768차원이어야 함)
    if (embeddingVector.length == 768) {
      lineLog("✅ 임베딩 차원 검증 성공: 768차원");
    } else {
      lineLog("❌ 임베딩 차원 검증 실패: " + embeddingVector.length + "차원");
    }
  }

  /**
   * EmbeddingService를 통한 아이템 임베딩 생성 테스트
   */
  public void generateItemEmbedding_TEST() {
    String itemText = "아이폰 15 프로 맥스 256GB 블루 미개봉 새상품";
    UUID itemId = UUID.randomUUID();

    lineLog("아이템 임베딩 생성 테스트");
    lineLog("아이템 텍스트: " + itemText);
    lineLog("아이템 ID: " +  itemId);

    embeddingService.generateAndSaveItemEmbedding(itemText, itemId);

    lineLog("아이템 임베딩 생성 완료");
  }

  private String getFirst10Elements(float[] arr) {
    StringBuilder sb = new StringBuilder("[");
    int limit = Math.min(10, arr.length);
    for (int i = 0; i < limit; i++) {
      sb.append(String.format("%.6f", arr[i]));
      if (i < limit - 1) sb.append(", ");
    }
    sb.append(", ...]");
    return sb.toString();
  }

  public void generateDummyEmbedding_TEST(){
    String testQuery = "테스트 쿼리";
  }
}