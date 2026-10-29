package com.romrom.ai.event;

import com.romrom.common.constant.OriginalType;
import java.util.UUID;

/**
 * 임베딩 갱신 요청: 원본(아이템·회원 카테고리) 저장 트랜잭션 커밋 후 처리
 */
public record EmbeddingRefreshRequestedEvent(UUID originalId, OriginalType originalType, String sourceText) {
}
