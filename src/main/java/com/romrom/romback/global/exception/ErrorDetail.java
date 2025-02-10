package com.romrom.romback.global.exception;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ErrorDetail {
    private final String errorCode;
    private final String errorMessage;
    private final Map<String, String> validation;
}
