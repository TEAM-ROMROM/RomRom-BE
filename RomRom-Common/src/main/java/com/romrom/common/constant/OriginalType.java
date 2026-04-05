package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OriginalType {
    ITEM("물품"),
    CATEGORY("카테고리"),
    ITEM_CATEGORY("물품 카테고리"); // 카테고리 매칭 시 사용

    private final String description;
} 