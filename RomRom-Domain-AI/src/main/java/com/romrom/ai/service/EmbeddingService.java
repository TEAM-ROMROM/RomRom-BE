package com.romrom.ai.service;

import com.google.genai.types.EmbedContentResponse;
import com.romrom.ai.EmbeddingUtil;
import com.romrom.ai.properties.SuhAiderProperties;
import com.romrom.common.constant.OriginalType;
import com.romrom.common.entity.postgres.Embedding;
import com.romrom.common.repository.EmbeddingRepository;
import com.romrom.common.util.CommonUtil;
import kr.suhsaechan.ai.service.SuhAiderEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

  private final EmbeddingRepository embeddingRepository;
  private final VertexAiClient vertexAiClient;
  private final SuhAiderEngine suhAiderEngine;
  private final SuhAiderProperties suhAiderProperties;

  /**
   * 임베딩 갱신(upsert): 벡터 생성 > 최신 임베딩 있으면 벡터만 교체, 없으면 신규 저장
   * AI 생성 실패만 건너뜀 (원본 저장은 유지), DB 실패는 호출 측으로 전파
   */
  @Transactional
  public void refreshEmbedding(UUID originalId, OriginalType originalType, String sourceText) {
    float[] embeddingVector = generateEmbeddingVectorOrNull(sourceText);
    if (embeddingVector == null) {
      return;
    }

    embeddingRepository
        .findFirstByOriginalIdAndOriginalTypeOrderByCreatedDateDesc(originalId, originalType)
        .ifPresentOrElse(
            existingEmbedding -> existingEmbedding.setEmbedding(embeddingVector),
            () -> embeddingRepository.save(Embedding.builder()
                .originalId(originalId)
                .embedding(embeddingVector)
                .originalType(originalType)
                .build())
        );
    log.debug("임베딩 갱신 완료: originalId={}, originalType={}", originalId, originalType);
  }

  /**
   * 아이템 임베딩 삭제
   */
  @Transactional
  public void deleteItemEmbedding(UUID itemId) {
    int deletedCount = embeddingRepository.deleteByOriginalIdAndOriginalType(itemId, OriginalType.ITEM);
    log.debug("아이템 임베딩 삭제 완료: itemId={}, 삭제건수={}", itemId, deletedCount);
  }

  /**
   * 회원 선호 카테고리 임베딩 삭제
   */
  @Transactional
  public void deleteMemberCategoryEmbedding(UUID memberId) {
    int deletedCount = embeddingRepository.deleteByOriginalIdAndOriginalType(memberId, OriginalType.CATEGORY);
    log.debug("회원 선호 카테고리 임베딩 삭제 완료: memberId={}, 삭제건수={}", memberId, deletedCount);
  }

  /**
   * 임베딩 벡터 생성, 외부 AI 실패 시 null (SUH-AIder·Vertex AI 모두 실패)
   */
  private float[] generateEmbeddingVectorOrNull(String text) {
    try {
      return generateEmbeddingVector(text);
    } catch (Exception embeddingGenerationException) {
      log.error("임베딩 벡터 생성 실패 (임베딩 저장 건너뜀): {}", embeddingGenerationException.getMessage(), embeddingGenerationException);
      return null;
    }
  }

  /**
   * 텍스트 정규화 및 임베딩 생성 후 벡터 추출 SUH-AIder 사용 > 실패 시 Vertex AI fallback
   */
  public float[] generateEmbeddingVector(String text) {
    log.debug("임베딩 생성 요청, 텍스트 정규화, 임베딩 생성, 벡터 추출 순서로 진행: {}", text);
    // 텍스트 정규화
    String normalized = CommonUtil.normalizeSpaces(text);
    log.debug("Normalized text: \"{}\"", normalized);

    // 요청 시작 시각
    long startMs = System.currentTimeMillis();

    // SUH-AIder (Embedding)
    try {
      String embeddingModel = suhAiderProperties.getEmbedding().getDefaultModel();
      List<Double> embedding = suhAiderEngine.embed(embeddingModel, normalized);
      float[] embeddingVector = CommonUtil.convertDoubleListToFloatArray(embedding);
      log.debug("SUH-AIder 임베딩 생성 완료: 차원={}, 지연시간={}ms",
          embeddingVector.length, System.currentTimeMillis() - startMs);
      return embeddingVector;
    } catch (Exception e) {
      log.warn("SUH-AIder 임베딩 실패, Vertex AI fallback 시도: {}", e.getMessage());
    }

    // Fallback: Vertex AI (Embedding)
    EmbedContentResponse response = vertexAiClient.generateEmbedding(normalized);
    float[] embeddingVector = EmbeddingUtil.extractVector(response);
    log.debug("Vertex AI fallback 임베딩 완료: 차원={}, 지연시간={}ms",
        embeddingVector.length, System.currentTimeMillis() - startMs);
    return embeddingVector;
  }

  /**
   * 아이템 임베딩 일괄 삭제
   *
   * @param itemIds 삭제할 아이템 ID 리스트
   */
  @Transactional
  public void deleteItemEmbeddings(List<UUID> itemIds) {
    if (itemIds == null || itemIds.isEmpty()) {
      return;
    }

    embeddingRepository.deleteAllByOriginalIdsAndType(itemIds, OriginalType.ITEM);
    log.debug("아이템 임베딩 일괄 삭제 완료: count={}", itemIds.size());
  }
}
