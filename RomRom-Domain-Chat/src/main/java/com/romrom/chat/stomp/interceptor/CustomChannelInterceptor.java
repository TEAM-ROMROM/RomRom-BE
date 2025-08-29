package com.romrom.chat.stomp.interceptor;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.jwt.JwtUtil;
import com.romrom.auth.service.CustomUserDetailsService;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Map;

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
  public static final String SESSION_USER_KEY = "user";

  private final JwtUtil jwtUtil;
  private final CustomUserDetailsService customUserDetailsService;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel messageChannel) {
    /** StompHeaderAccessor는 일회성 객체
     * 따라서 CONNECT 에서 setUser()로 설정한 사용자 정보는 Principal 타입으로 저장됨
     * Principal 타입은 getName()밖에 못쓰므로 토큰 만료 검증 불가능
     * 그래서 세션에 사용자 정보를 저장해두고, SUBSCRIBE, SEND 등에서 세션을 통해 사용자 정보를 조회하도록 구현
     * 그 원리는 StompHeaderAccessor.getSessionAttributes()가 HttpSession이 아닌, WebSocket 세션을 반환하기 때문
     * WebSocket 세션은 CONNECT 시점부터 유지되므로, 세션에 저장한 사용자 정보도 계속 조회 가능
    */
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    StompCommand command = accessor.getCommand();
    if (command == null) {
      return message;
    }

    log.debug("{} 요청 - 현재 목적지 주소: {}", command, accessor.getDestination());
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
    accessor.setUser(customUserDetails); // StompHeaderAccessor의 setUser()는 principal 타입 -> getName() 밖에 못함
    // 그러나 MessageMapping 에서 스프링이 user를 자동으로 넣어주므로 이 메서드는 꼭 필요함

    // 그래서 세션에 직접 사용자 정보를 저장
    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
    if (sessionAttributes != null) {
      sessionAttributes.put("user", customUserDetails); // "user"라는 키로 저장
      log.debug("세션에 사용자 정보 저장 완료: {}", customUserDetails.getMemberId());
    } else {
      log.error("세션 속성을 가져올 수 없습니다.");
    }

    log.debug("CONNECT 요청: 사용자 {} 인증 완료", customUserDetails.getMemberId());
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
    // accessor.getUser() -> @Presend 메서드가 끝난 이후에 정보가 채워지기 떄문에, null이 반환됨
    // 따라서 세션으로 사용자 정보를 조회해야 함
    CustomUserDetails customUserDetails = (CustomUserDetails) accessor.getSessionAttributes().get(SESSION_USER_KEY);

    if (customUserDetails == null) {
      log.error("세션에서 사용자 정보를 찾을 수 없습니다. 인증된 사용자가 아닙니다.");
      throw new CustomException(ErrorCode.UNAUTHORIZED);
    }

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
