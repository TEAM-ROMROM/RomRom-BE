package com.romrom.ai.service;

import com.romrom.ai.EmbeddingUtil;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.OriginalType;
import com.romrom.common.entity.postgres.Embedding;
import com.romrom.common.repository.EmbeddingRepository;
import com.romrom.common.repository.EmbeddingRepository.CategoryDistanceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryMatchingService {

  private static final int DEFAULT_TOP_N = 3;
  // 코사인 유사도 0.54 이상 = 코사인 거리 0.46 이하
  private static final float MAX_COSINE_DISTANCE = 0.46f;
  // 1위 대비 거리 차이가 이 값 초과면 제외
  private static final double MAX_RELATIVE_GAP = 0.07;

  private final EmbeddingService embeddingService;
  private final EmbeddingRepository embeddingRepository;

  /**
   * 서버 시작 시 카테고리 임베딩 초기화
   * DB에 저장된 임베딩은 스킵하고, 없는 카테고리만 생성 후 저장
   */
  public void initializeCategoryEmbeddings() {
    log.info("카테고리 임베딩 초기화 시작: {} 개", ItemCategory.values().length);
    int loadedCount = 0;
    int generatedCount = 0;

    for (ItemCategory itemCategory : ItemCategory.values()) {
      UUID categoryUuid = toCategoryUuid(itemCategory);

      boolean alreadyExists = embeddingRepository
          .findFirstByOriginalIdAndOriginalTypeOrderByCreatedDateDesc(categoryUuid, OriginalType.ITEM_CATEGORY)
          .isPresent();

      if (alreadyExists) {
        loadedCount++;
        continue;
      }

      try {
        float[] categoryEmbeddingVector = embeddingService.generateEmbeddingVector(itemCategory.getDescription());
        embeddingRepository.save(Embedding.builder()
            .originalId(categoryUuid)
            .embedding(categoryEmbeddingVector)
            .originalType(OriginalType.ITEM_CATEGORY)
            .build());
        generatedCount++;
        log.debug("카테고리 임베딩 생성 및 저장: {}", itemCategory.name());
      } catch (Exception e) {
        log.warn("카테고리 임베딩 저장 실패 (스킵): category={}", itemCategory.name(), e);
      }
    }

    log.info("카테고리 임베딩 초기화 완료: 기존유지={}, 신규생성={}", loadedCount, generatedCount);
  }

  /**
   * 물품명을 기반으로 pgvector 코사인 거리 기반 상위 N개 카테고리 반환
   * - OTHER는 매칭 후보에서 제외
   * - 절대 임계값(MAX_COSINE_DISTANCE) 이내인 카테고리만 반환
   * - 1위 대비 거리 차이가 MAX_RELATIVE_GAP 초과면 제외
   * - 해당 카테고리가 없으면 OTHER 단독 반환
   *
   * @param itemName 물품명
   * @return 추천 카테고리 목록 (1~3개)
   */
  public List<ItemCategory> matchTopCategories(String itemName) {
    float[] itemNameEmbeddingVector = embeddingService.generateEmbeddingVector(itemName);
    String itemNameVectorLiteral = EmbeddingUtil.toVectorLiteral(itemNameEmbeddingVector);

    List<CategoryDistanceResult> topResults = embeddingRepository.findTopSimilarItemCategoryIds(
        itemNameVectorLiteral, MAX_COSINE_DISTANCE, DEFAULT_TOP_N + 1);

    // OTHER 제외 후 gap 기준으로 사용할 최상위 카테고리 선정
    List<CategoryDistanceResult> nonOtherResults = topResults.stream()
        .filter(result -> fromCategoryUuid(result.getOriginalId()) != ItemCategory.OTHER)
        .toList();

    if (nonOtherResults.isEmpty()) {
      log.debug("임계값 이내 카테고리 없음, OTHER 반환: itemName={}", itemName);
      return List.of(ItemCategory.OTHER);
    }

    double bestNonOtherDistance = nonOtherResults.get(0).getDistance();

    List<ItemCategory> matchedCategories = nonOtherResults.stream()
        .filter(result -> result.getDistance() - bestNonOtherDistance <= MAX_RELATIVE_GAP)
        .map(result -> fromCategoryUuid(result.getOriginalId()))
        .limit(DEFAULT_TOP_N)
        .toList();

    if (matchedCategories.isEmpty()) {
      log.debug("gap 초과로 유효 카테고리 없음, OTHER 반환: itemName={}", itemName);
      return List.of(ItemCategory.OTHER);
    }

    return matchedCategories;
  }

  /**
   * ItemCategory code → UUID 변환 (DB originalId 저장용)
   * UUID(0L, code) 형태로 고정값 생성하여 역변환 가능
   */
  private static UUID toCategoryUuid(ItemCategory itemCategory) {
    return new UUID(0L, (long) itemCategory.getCode());
  }

  private static ItemCategory fromCategoryUuid(UUID categoryUuid) {
    return ItemCategory.fromCode((int) categoryUuid.getLeastSignificantBits());
  }
}
