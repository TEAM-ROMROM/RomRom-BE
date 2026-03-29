package com.romrom.ai.service;

import com.romrom.common.constant.ItemCategory;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryMatchingService {

  private static final int DEFAULT_TOP_N = 3;
  // 절대 임계값: 이 점수 미만은 무조건 제외
  private static final float MINIMUM_SIMILARITY_THRESHOLD = 0.45f;
  // 상대 임계값: 1위 점수 대비 이 차이를 초과하면 제외
  private static final float MAX_SCORE_GAP_FROM_TOP = 0.10f;

  private final EmbeddingService embeddingService;

  // 카테고리 임베딩 인메모리 캐시 (서버 시작 시 초기화)
  private final Map<ItemCategory, float[]> categoryEmbeddingCache = new EnumMap<>(ItemCategory.class);

  /**
   * 서버 시작 시 모든 ItemCategory 임베딩을 생성해 메모리에 캐시
   */
  public void initializeCategoryEmbeddings() {
    log.info("카테고리 임베딩 캐시 초기화 시작: {} 개", ItemCategory.values().length);

    for (ItemCategory itemCategory : ItemCategory.values()) {
      try {
        float[] categoryEmbeddingVector = embeddingService.generateEmbeddingVector(itemCategory.getDescription());
        categoryEmbeddingCache.put(itemCategory, categoryEmbeddingVector);
        log.debug("카테고리 임베딩 캐시 저장: {}", itemCategory.name());
      } catch (Exception e) {
        log.error("카테고리 임베딩 생성 실패: {}", itemCategory.name(), e);
      }
    }

    log.info("카테고리 임베딩 캐시 초기화 완료: {} 개", categoryEmbeddingCache.size());
  }

  /**
   * 물품명을 기반으로 코사인 유사도 상위 N개 카테고리 반환
   * - OTHER는 매칭 후보에서 제외
   * - 임계값(MINIMUM_SIMILARITY_THRESHOLD) 이상인 카테고리만 반환 (최대 3개)
   * - 1위 점수와의 차이가 MAX_SCORE_GAP_FROM_TOP 초과하면 제외
   * - 임계값을 넘는 카테고리가 없으면 OTHER 단독 반환
   *
   * @param itemName 물품명
   * @return 추천 카테고리 목록 (1~3개)
   */
  public List<ItemCategory> matchTopCategories(String itemName) {
    float[] itemNameEmbeddingVector = embeddingService.generateEmbeddingVector(itemName);

    List<Map.Entry<ItemCategory, Float>> scoredCategories = categoryEmbeddingCache.entrySet().stream()
        .filter(entry -> entry.getKey() != ItemCategory.OTHER)
        .map(entry -> Map.entry(entry.getKey(), computeCosineSimilarity(itemNameEmbeddingVector, entry.getValue())))
        .sorted(Map.Entry.<ItemCategory, Float>comparingByValue().reversed())
        .toList();

    float topScore = scoredCategories.isEmpty() ? 0f : scoredCategories.get(0).getValue();

    List<ItemCategory> matchedCategories = scoredCategories.stream()
        .filter(scoredEntry -> scoredEntry.getValue() >= MINIMUM_SIMILARITY_THRESHOLD)
        .filter(scoredEntry -> topScore - scoredEntry.getValue() <= MAX_SCORE_GAP_FROM_TOP)
        .limit(DEFAULT_TOP_N)
        .map(Map.Entry::getKey)
        .toList();

    if (matchedCategories.isEmpty()) {
      log.debug("임계값({}) 이상 카테고리 없음, OTHER 반환: itemName={}", MINIMUM_SIMILARITY_THRESHOLD, itemName);
      return List.of(ItemCategory.OTHER);
    }

    return matchedCategories;
  }

  private float computeCosineSimilarity(float[] vectorA, float[] vectorB) {
    float dotProduct = 0f;
    float normA = 0f;
    float normB = 0f;
    for (int i = 0; i < vectorA.length; i++) {
      dotProduct += vectorA[i] * vectorB[i];
      normA += vectorA[i] * vectorA[i];
      normB += vectorB[i] * vectorB[i];
    }
    if (normA == 0f || normB == 0f) {
      return 0f;
    }
    return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
  }
}
