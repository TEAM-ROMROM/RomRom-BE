package com.romrom.chat.stomp.exception;

import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * 프로토콜 레벨 에러 핸들러
 * STOMP 프레임 파싱/라우팅/보안 예외를 JSON ERROR 프레임으로 내려보내고 세션 종료 (ex. 잘못된 헤더, 권한 없음)
 */
@Component
@Slf4j
public class CustomStompProtocolErrorHandler extends StompSubProtocolErrorHandler {

  @Override
  public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
    // cause/getMessage() 모두 null일 수 있으므로 최종 fallback으로 클래스명 사용
    Throwable cause = ex.getCause();
    String errorMessage =
        (cause != null && cause.getMessage() != null) ? cause.getMessage()
            : (ex.getMessage() != null) ? ex.getMessage()
            : ex.getClass().getSimpleName();

    log.error("웹소켓 오류 발생: {}", ex.getMessage());

    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
    accessor.setMessage("CustomException: " + errorMessage);
    accessor.setLeaveMutable(true);

    String payload = "CustomException 발생: " + errorMessage;
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

    return MessageBuilder.createMessage(bytes, accessor.getMessageHeaders());
  }
}
