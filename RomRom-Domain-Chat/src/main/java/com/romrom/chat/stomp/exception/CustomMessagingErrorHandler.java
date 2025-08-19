package com.romrom.chat.stomp.exception;

import com.romrom.chat.stomp.properties.WebSocketProperties;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.exception.ErrorResponse;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 메시징 예외 핸들러
 * 메시지 전송/파싱 중 예외를 내려보내고 세션 유지
 */
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class CustomMessagingErrorHandler {

  private final WebSocketProperties webSocketProperties;
  private final SimpMessagingTemplate template;

  @MessageExceptionHandler(CustomException.class)
  public void handleCustomException(Principal principal, CustomException e) {
    log.error("WebSocket 통신 중 CustomException 발생: {}", e.getMessage());
    ErrorResponse response = ErrorResponse.builder()
        .errorCode(e.getErrorCode())
        .errorMessage(e.getMessage())
        .build();
    template.convertAndSendToUser(principal.getName(), webSocketProperties.errorDestination(), response);
  }

  @MessageExceptionHandler(Exception.class)
  public void handleException(Principal principal, Exception e) {
    log.error("Unhandled Exception 발생: {}", e.getMessage(), e);
    // 예상치 못한 에러 => 500
    ErrorResponse response = ErrorResponse.builder()
        .errorCode(ErrorCode.INTERNAL_SERVER_ERROR)
        .errorMessage(ErrorCode.INTERNAL_SERVER_ERROR.getMessage())
        .build();
    template.convertAndSendToUser(principal.getName(), webSocketProperties.errorDestination(), response);
  }
}
