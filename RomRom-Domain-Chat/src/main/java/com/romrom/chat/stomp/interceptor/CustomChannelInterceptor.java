package com.romrom.chat.stomp.interceptor;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.jwt.JwtUtil;
import com.romrom.auth.service.CustomUserDetailsService;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomChannelInterceptor implements ChannelInterceptor {

  private static final String SUB_PREFIX = "/sub/";
  private static final String EXCHANGE_PREFIX = "/exchange/chat.exchange/";

  private final JwtUtil jwtUtil;
  private final CustomUserDetailsService customUserDetailsService;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel messageChannel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    StompCommand command = accessor.getCommand();
    if (command == null) {
      return message;
    }

    switch (command) {
      case CONNECT -> { // STOMP 연결 요청
        return handleConnect(message, accessor);
      }
      case SUBSCRIBE -> { // STOMP 구독 요청
        validatePrincipalExpiration(accessor);
        return handleInboundSubscribe(message, accessor);
      }
      case SEND -> { // 클라이언트 -> 서버(브로커) 메시지 발행
        validatePrincipalExpiration(accessor);
        return message;
      }
      case MESSAGE -> { // 서버(브로커) -> 클라이언트 발행된 메시지
        return handleOutboundMessage(message, accessor);
      }
      default -> {
        return message;
      }
    }
  }

  /**
   * CONNECT 요청 핸들러
   */
  private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
    String bearerToken = accessor.getFirstNativeHeader("Authorization");
    if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
      log.error("CONNECT 요청: Authorization 헤더가 없거나 형식이 잘못됨");
      throw new CustomException(ErrorCode.UNAUTHORIZED);
    }
    String token = bearerToken.substring(7);
    if (!jwtUtil.validateToken(token)) {
      log.error("토큰이 유효하지 않습니다.");
      throw new CustomException(ErrorCode.INVALID_ACCESS_TOKEN);
    }
    CustomUserDetails customUserDetails = customUserDetailsService.loadUserByUsername(jwtUtil.getUsername(token));
    customUserDetails.confirmExpire(jwtUtil.getRemainingValidationMilliSecond(token));
    accessor.setUser(customUserDetails);
    return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
  }

  /**
   * INBOUND 구독 핸들러 (Subscribe)
   * 클라이언트 -> 서버
   */
  private Message<?> handleInboundSubscribe(Message<?> message, StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    log.debug("INBOUND SUBSCRIBE - 원래 구독 주소: {}", destination);
    if (destination != null && destination.startsWith(SUB_PREFIX)) {
      String convertedDestination = convertToExchangeDestination(destination);
      log.debug("INBOUND SUBSCRIBE - 변경된 구독 주소: {}", convertedDestination);
      accessor.setDestination(convertedDestination);
      return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }
    return message;
  }

  /**
   * OUTBOUND 메시지 핸들러 (Message)
   * 서버(브로커) -> 클라이언트
   */
  private Message<?> handleOutboundMessage(Message<?> message, StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    log.debug("OUTBOUND SUBSCRIBE - 원래 발행 주소: {}", destination);
    if (destination != null && destination.startsWith(EXCHANGE_PREFIX)) {
      String convertedDestination = convertToSubDestination(destination);
      log.debug("OUTBOUND SUBSCRIBE - 변경된 발행 주소: {}", convertedDestination);
      accessor.setDestination(convertedDestination);
      return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }
    return message;
  }

  /**
   * 요청 Principal 만료 검증
   */
  private void validatePrincipalExpiration(StompHeaderAccessor accessor) {
    CustomUserDetails customUserDetails = (CustomUserDetails) accessor.getUser();
    LocalDateTime expiresAt = customUserDetails.getExpiresAt();
    if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
      log.error("사용자: {} 토큰 만료", customUserDetails.getMemberId());
      throw new CustomException(ErrorCode.EXPIRED_ACCESS_TOKEN);
    }
  }

  private String convertToExchangeDestination(String destination) {
    return destination.replace(SUB_PREFIX, EXCHANGE_PREFIX);
  }

  private String convertToSubDestination(String destination) {
    return destination.replace(EXCHANGE_PREFIX, SUB_PREFIX);
  }
}
