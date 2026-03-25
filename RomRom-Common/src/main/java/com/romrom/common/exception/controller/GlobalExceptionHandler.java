package com.romrom.common.exception.controller;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.exception.ErrorResponse;
import com.romrom.common.exception.UgcProhibitedContentException;
import com.romrom.common.exception.UgcViolationResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.HeuristicCompletionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(UgcProhibitedContentException.class)
  public ResponseEntity<UgcViolationResponse> handleUgcProhibitedContentException(UgcProhibitedContentException e) {
    log.warn("UGC 유해 콘텐츠 감지: field={}, violatingText={}", e.getFieldName(), e.getViolatingText());
    UgcViolationResponse response = UgcViolationResponse.builder()
        .errorCode(ErrorCode.PROHIBITED_CONTENT.name())
        .errorMessage(e.getMessage())
        .violatingText(e.getViolatingText())
        .fieldName(e.getFieldName())
        .build();
    return ResponseEntity.status(ErrorCode.PROHIBITED_CONTENT.getStatus()).body(response);
  }

  @ExceptionHandler(CustomException.class)
  public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
    log.error("CustomException 발생: {}", e.getMessage(), e);
    ErrorCode errorCode = e.getErrorCode();
    ErrorResponse response = ErrorResponse.builder()
        .errorCode(errorCode)
        .errorMessage(e.getMessage())
        .build();
    return ResponseEntity.status(errorCode.getStatus()).body(response);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleException(Exception e) {
    log.error("Unhandled Exception 발생: {}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
  }

  // 몽고DB와 엮인 트랜잭션에서 PG 성공 후 Mongo 실패 시 발생할 수 있는 특수한 예외
  @ExceptionHandler(HeuristicCompletionException.class)
  public ResponseEntity<ErrorResponse> handleHeuristicCompletionException(HeuristicCompletionException e, HttpServletRequest request) {
    log.error("[CRITICAL DATA INCONSISTENCY] HeuristicCompletionException 발생!");
    log.error("Request URL: {}", request.getRequestURI());
    log.error("Exception Message: {}", e.getMessage());

    // sendAlertToAdmin(e);

    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.builder()
            .errorCode(ErrorCode.INTERNAL_SERVER_ERROR)
            .errorMessage("서버 내부에서 트랜잭션 동기화 중 오류가 발생했습니다. 관중 중인 데이터가 불일치할 수 있으니 확인이 필요합니다.")
            .build());
  }
}
