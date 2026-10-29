package com.romrom.common.exception.controller;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.EmailAlreadyRegisteredException;
import com.romrom.common.exception.EmailAlreadyRegisteredResponse;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.exception.ErrorResponse;
import com.romrom.common.exception.SuspendedMemberException;
import com.romrom.common.exception.SuspendedMemberResponse;
import com.romrom.common.exception.UgcProhibitedContentException;
import com.romrom.common.exception.UgcViolationResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.CompletionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.HeuristicCompletionException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(SuspendedMemberException.class)
  public ResponseEntity<SuspendedMemberResponse> handleSuspendedMemberException(SuspendedMemberException e) {
    log.warn("제재된 회원 요청 차단: suspendReason={}, suspendedUntil={}", e.getSuspendReason(), e.getSuspendedUntil());
    SuspendedMemberResponse suspendedMemberResponse = SuspendedMemberResponse.builder()
        .errorCode(ErrorCode.SUSPENDED_MEMBER.name())
        .suspendReason(e.getSuspendReason())
        .suspendedUntil(e.getSuspendedUntil())
        .build();
    return ResponseEntity.status(ErrorCode.SUSPENDED_MEMBER.getStatus()).body(suspendedMemberResponse);
  }

  @ExceptionHandler(EmailAlreadyRegisteredException.class)
  public ResponseEntity<EmailAlreadyRegisteredResponse> handleEmailAlreadyRegisteredException(
      EmailAlreadyRegisteredException e) {
    log.warn("이메일 중복 소셜 플랫폼 로그인 시도: registeredSocialPlatform={}", e.getRegisteredSocialPlatform());
    EmailAlreadyRegisteredResponse emailAlreadyRegisteredResponse = EmailAlreadyRegisteredResponse.builder()
        .errorCode(ErrorCode.EMAIL_ALREADY_REGISTERED.name())
        .registeredSocialPlatform(e.getRegisteredSocialPlatform())
        .build();
    return ResponseEntity.status(ErrorCode.EMAIL_ALREADY_REGISTERED.getStatus())
        .body(emailAlreadyRegisteredResponse);
  }

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

  @ExceptionHandler(CompletionException.class)
  public ResponseEntity<?> handleCompletionException(CompletionException e) {
    Throwable cause = e.getCause();
    if (cause instanceof CustomException customException) {
      return handleCustomException(customException);
    }
    if (cause instanceof UgcProhibitedContentException ugcException) {
      return handleUgcProhibitedContentException(ugcException);
    }
    log.error("CompletionException 내 비처리 예외 발생: {}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.builder()
            .errorCode(ErrorCode.INTERNAL_SERVER_ERROR)
            .errorMessage("서버 내부 오류가 발생했습니다.")
            .build());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
    // 프레임워크 요청 오류(경로 없음·메서드·바인딩)는 원래 4xx 유지
    HttpStatusCode clientErrorStatus = resolveFrameworkClientErrorStatus(e);
    if (clientErrorStatus != null) {
      ErrorCode clientErrorCode = toClientErrorCode(clientErrorStatus);
      log.warn("[request] rejected {} : {} {} ({})", clientErrorStatus.value(), request.getMethod(),
          request.getRequestURI(), e.getClass().getSimpleName());
      return ResponseEntity.status(clientErrorStatus)
          .body(ErrorResponse.builder()
              .errorCode(clientErrorCode)
              .errorMessage(clientErrorCode.getMessage())
              .build());
    }

    log.error("Unhandled Exception 발생: {}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ErrorResponse.builder()
            .errorCode(ErrorCode.INTERNAL_SERVER_ERROR)
            .errorMessage("서버 내부 오류가 발생했습니다.")
            .build());
  }

  /**
   * 프레임워크 4xx 상태 추출 (404 경로 없음, 405 메서드, 415 미디어 타입, 400 본문·타입·멀티파트 오류), 해당 없으면 null
   */
  private HttpStatusCode resolveFrameworkClientErrorStatus(Exception e) {
    if (e instanceof org.springframework.web.ErrorResponse frameworkErrorResponse
        && frameworkErrorResponse.getStatusCode().is4xxClientError()) {
      return frameworkErrorResponse.getStatusCode();
    }
    if (e instanceof HttpMessageNotReadableException
        || e instanceof TypeMismatchException
        || e instanceof MultipartException) {
      return HttpStatus.BAD_REQUEST;
    }
    return null;
  }

  /**
   * HTTP 상태 > 에러코드: 404 API_NOT_FOUND, 405 METHOD_NOT_ALLOWED, 그 외 INVALID_REQUEST
   */
  private ErrorCode toClientErrorCode(HttpStatusCode clientErrorStatus) {
    return switch (clientErrorStatus.value()) {
      case 404 -> ErrorCode.API_NOT_FOUND;
      case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
      default -> ErrorCode.INVALID_REQUEST;
    };
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
            .errorMessage("서버 내부에서 트랜잭션 동기화 중 오류가 발생했습니다. 관리 중인 데이터가 불일치할 수 있으니 확인이 필요합니다.")
            .build());
  }
}
