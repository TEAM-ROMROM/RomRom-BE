package com.romrom.ai.service;

import com.romrom.common.constant.OriginalType;
import com.romrom.common.entity.postgres.Embedding;
import com.romrom.common.repository.EmbeddingRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingRepository embeddingRepository;

    /**
     * 아이템 임베딩 생성 및 저장
     */
    @Transactional
    public void generateAndSaveItemEmbedding(String itemText, UUID itemId) {
        try {
            // 아이템 정보를 기반으로 임베딩 생성
            float[] embeddingVector = generateDummyEmbedding(itemText);

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
    public void generateAndSaveMemberItemCategoryEmbedding(List<?> memberItemCategories) {
        try {
            if (memberItemCategories == null || memberItemCategories.isEmpty()) {
                log.debug("카테고리 리스트가 비어있어 임베딩 생성을 건너뜁니다.");
                return;
            }

            // 카테고리 정보를 기반으로 임베딩 생성
            String categoryText = extractCategoryText(memberItemCategories);
            float[] embeddingVector = generateDummyEmbedding(categoryText);

            UUID memberId = extractMemberId(memberItemCategories);

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
     * 임시 더미 임베딩 생성 (실제 구현시 AI 서비스로 대체)
     */
    public float[] generateDummyEmbedding(String text) {
        log.debug("더미 임베딩 생성 요청: {}", text);

        // 임시 더미 임베딩 (실제 구현시 AI 서비스 호출로 대체)
        float[] embedding = new float[384];
        for (int i = 0; i < 384; i++) {
            embedding[i] = (float) Math.random();
        }

        log.debug("더미 임베딩 생성 완료: 차원={}", embedding.length);
        return embedding;
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