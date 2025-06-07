package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.constant.OriginalType;
import com.romrom.romback.domain.object.postgres.Embedding;
import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.MemberItemCategory;
import com.romrom.romback.domain.repository.postgres.EmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final VertexAiClient vertexAiClient;
    private final EmbeddingRepository embeddingRepository;

    public void generateAndSaveItemEmbedding(Item item) {
        // todo : itemDescription 넣을까 말까 고민인데 조언좀욧 !
        String text = item.getItemName() + " " + item.getItemDescription();

        // VertexAi 에게서 임베딩 값 가져오기
        float[] vector = vertexAiClient.getTextEmbedding(text);

        // 임베딩 객체 생성
        Embedding itemEmbedding = Embedding.builder()
                .embedding(vector)
                .originalId(item.getItemId())
                .originalType(OriginalType.ITEM)
                .build();

        embeddingRepository.save(itemEmbedding);
    }
    public void generateAndSaveMemberItemCategoryEmbedding(List<MemberItemCategory> memberItemCategories) {

        // VertexAi 에게서 임베딩 값 가져오기
        List<String> descriptions = memberItemCategories.stream()
                .map(mic -> mic.getItemCategory().getDescription())
                .toList();

        log.info("총 " + descriptions.size() + "개의 아이템 임베딩값 생성중 : " + descriptions);
        List<float[]> vectors = vertexAiClient.getTextEmbeddings(descriptions); // 여러 개 한 번에 받기

        for (int i = 0; i < memberItemCategories.size(); i++) {
            MemberItemCategory mic = memberItemCategories.get(i);
            float[] vector = vectors.get(i);

            Embedding embedding = Embedding.builder()
                    .embedding(vector)
                    .originalId(mic.getMemberItemCategoryId())
                    .originalType(OriginalType.CATEGORY)
                    .build();

            log.debug("embedding 값을 저장합니다, 카테고리 id : " + mic.getMemberItemCategoryId());
            embeddingRepository.save(embedding);
        }

    }

}
