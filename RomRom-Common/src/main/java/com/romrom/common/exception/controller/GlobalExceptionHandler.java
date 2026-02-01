package com.romrom.common.exception.controller;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.exception.ErrorResponse;
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

  /**
   * 1) 커스텀 예외 처리
   */
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

  /**
   * 2) 그 외 예외 처리
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleException(Exception e) {
    log.error("Unhandled Exception 발생: {}", e.getMessage(), e);

    // 예상치 못한 예외 => 500
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
  }

  // 3) HeuristicCompletionException 처리 - 몽고DB와 엮인 트랜잭션에서 발생할 수 있는 특수한 예외
  @ExceptionHandler(HeuristicCompletionException.class)
  public ResponseEntity<ErrorResponse> handleHeuristicCompletionException(HeuristicCompletionException e, HttpServletRequest request) {
    // 1. 아주 상세한 로그 기록 (누가, 어떤 API에서 발생했는지)
    log.error("[CRITICAL DATA INCONSISTENCY] HeuristicCompletionException 발생!");
    log.error("Request URL: {}", request.getRequestURI());
    log.error("Exception Message: {}", e.getMessage());

    // 2. 금융 IT라면 여기서 슬랙(Slack)이나 모니터링 툴(Sentry, ELK)로 즉시 알림을 쏘는 코드가 들어갑니다.
    // sendAlertToAdmin(e);

    // 3. 사용자에게는 서버 에러임을 알리되, 데이터 확인이 필요할 수 있음을 암시
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse(
            ErrorCode.INTERNAL_SERVER_ERROR,
            "서버 내부에서 트랜잭션 동기화 중 오류가 발생했습니다. 관중 중인 데이터가 불일치할 수 있으니 확인이 필요합니다."
        ));
  }
}
