package com.romrom.ai.service;

import com.google.genai.types.EmbedContentResponse;
import com.romrom.ai.EmbeddingUtil;
import com.romrom.common.constant.OriginalType;
import com.romrom.common.entity.postgres.Embedding;
import com.romrom.common.repository.EmbeddingRepository;
import java.util.List;
import java.util.UUID;

import com.romrom.ai.properties.SuhAiderProperties;
import com.romrom.common.util.CommonUtil;
import kr.suhsaechan.ai.service.SuhAiderEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingRepository embeddingRepository;
    private final VertexAiClient vertexAiClient;
    private final SuhAiderEngine suhAiderEngine;
    private final SuhAiderProperties suhAiderProperties;

    /**
     * 아이템 임베딩 생성 및 저장
     */
    @Transactional
    public void generateAndSaveItemEmbedding(String itemText, UUID itemId) {
        try {
            // 아이템 텍스트 기반 임베딩 생성
            float[] embeddingVector = generateAndExtractEmbeddingAfterNormalization(itemText);

            Embedding embedding = Embedding.builder()
                .originalId(itemId)
                .embedding(embeddingVector)
                .originalType(OriginalType.ITEM)
                .build();

            embeddingRepository.save(embedding);
            log.debug("아이템 임베딩 저장 완료: itemId={}", itemId);

        } catch (Exception e) {
            log.error("아이템 임베딩 생성 실패", e);
            // 임베딩 생성 실패해도 아이템 등록은 계속 진행
        }
    }

    /**
     * 아이템 임베딩 삭제
     *
     * @param itemId 삭제할 아이템 ID
     */
    @Transactional
    public void deleteItemEmbedding(UUID itemId) {
        try {
            int deletedCount = embeddingRepository.deleteByOriginalIdAndOriginalType(itemId, OriginalType.ITEM);
            log.debug("아이템 임베딩 삭제 완료: itemId={}, 삭제건수={}", itemId, deletedCount);
        } catch (Exception e) {
            log.error("아이템 임베딩 삭제 실패: itemId={}", itemId, e);
        }
    }

    /**
     * 회원 선호 카테고리 임베딩 생성 및 저장
     */
    @Transactional
    public void generateAndSaveMemberItemCategoryEmbedding(UUID memberId, String categoryText) {
        try {
            // 카테고리 정보를 기반으로 임베딩 생성
            float[] embeddingVector = generateAndExtractEmbeddingAfterNormalization(categoryText);

            Embedding embedding = Embedding.builder()
                .originalId(memberId)
                .embedding(embeddingVector)
                .originalType(OriginalType.CATEGORY)
                .build();

            embeddingRepository.save(embedding);
            log.debug("회원 선호 카테고리 임베딩 저장 완료: memberId={}", memberId);

        } catch (Exception e) {
            log.error("회원 선호 카테고리 임베딩 생성 실패", e);
            // 임베딩 생성 실패해도 카테고리 저장은 계속 진행
        }
    }

    /**
     * 회원 선호 카테고리 임베딩 삭제
     *
     * @param memberId 삭제할 회원 ID
     */
    @Transactional
    public void deleteMemberCategoryEmbedding(UUID memberId) {
        try {
            int deletedCount = embeddingRepository.deleteByOriginalIdAndOriginalType(memberId, OriginalType.CATEGORY);
            log.debug("회원 선호 카테고리 임베딩 삭제 완료: memberId={}, 삭제건수={}", memberId, deletedCount);
        } catch (Exception e) {
            log.error("회원 선호 카테고리 임베딩 삭제 실패: memberId={}", memberId, e);
        }
    }

    /**
     * 텍스트 정규화 및 임베딩 생성 후 벡터 추출
     * SUH-AIder 사용 > 실패 시 Vertex AI fallback
     */
    private float[] generateAndExtractEmbeddingAfterNormalization(String text) {
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

    // Helper methods - 현재는 더미 구현, 실제 구현시 적절한 타입으로 변경 필요
    private String extractItemText(Object item) {
        // TODO: 실제 Item 객체에서 itemName, itemDescription 등을 조합한 텍스트 추출
        log.debug("아이템 텍스트 추출 (더미): {}", item.getClass().getSimpleName());
        return "아이템 설명 텍스트";
    }

    private UUID extractItemId(Object item) {
        // TODO: 실제 Item 객체에서 itemId 추출
        log.debug("아이템 ID 추출 (더미): {}", item.getClass().getSimpleName());
        return UUID.randomUUID();
    }

    private String extractCategoryText(List<?> categories) {
        // TODO: 실제 MemberItemCategory 리스트에서 카테고리명들을 조합한 텍스트 추출
        log.debug("카테고리 텍스트 추출 (더미): {} 개 카테고리", categories.size());
        return "선호 카테고리 텍스트";
    }

    private UUID extractMemberId(List<?> categories) {
        // TODO: 실제 MemberItemCategory 리스트에서 첫 번째 항목의 memberId 추출
        log.debug("회원 ID 추출 (더미): {} 개 카테고리", categories.size());
        return UUID.randomUUID();
    }
}