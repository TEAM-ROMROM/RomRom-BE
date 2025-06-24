package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OriginalType {
    ITEM("물품"),
    CATEGORY("카테고리");

    private final String description;
} 