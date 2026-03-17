package com.romrom.common.aop;

import com.romrom.common.annotation.SecuredApi;
import com.romrom.common.properties.SecuredApiProperties;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.util.SignatureUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @SecuredApi 어노테이션이 적용된 API에 대해 HMAC + Timestamp 서명 검증을 수행하는 AOP Aspect
 */
@Aspect
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SecuredApiProperties.class)
@Slf4j
public class SecuredApiAspect {

  private static final String HEADER_SIGNATURE = "X-Signature";
  private static final String HEADER_TIMESTAMP = "X-Timestamp";

  private final SecuredApiProperties securedApiProperties;

  @Around("@annotation(com.romrom.common.annotation.SecuredApi) || "
      + "(@within(com.romrom.common.annotation.SecuredApi) "
      + "&& !@annotation(com.romrom.common.annotation.SecuredApi))")
  public Object verifySignature(ProceedingJoinPoint joinPoint) throws Throwable {
    HttpServletRequest request = getCurrentHttpRequest();

    String signature = request.getHeader(HEADER_SIGNATURE);
    String timestamp = request.getHeader(HEADER_TIMESTAMP);

    // 헤더 누락 검증
    if (signature == null || signature.isBlank() || timestamp == null || timestamp.isBlank()) {
      log.warn("SecuredApi 서명 헤더 누락 - URI: {}", request.getRequestURI());
      throw new CustomException(ErrorCode.MISSING_SIGNATURE_HEADER);
    }

    // Timestamp 만료 검증 (양방향: 클라이언트 시계 오차 허용)
    long requestTimestamp;
    try {
      requestTimestamp = Long.parseLong(timestamp);
    } catch (NumberFormatException e) {
      log.warn("SecuredApi 타임스탬프 형식 오류 - timestamp: {}", timestamp);
      throw new CustomException(ErrorCode.EXPIRED_SIGNATURE_TIMESTAMP);
    }

    long currentTime = System.currentTimeMillis();
    if (Math.abs(currentTime - requestTimestamp) > securedApiProperties.getExpirationTime()) {
      log.warn("SecuredApi 타임스탬프 만료 - 요청: {}, 현재: {}, 차이: {}ms",
          requestTimestamp, currentTime, Math.abs(currentTime - requestTimestamp));
      throw new CustomException(ErrorCode.EXPIRED_SIGNATURE_TIMESTAMP);
    }

    // HMAC 서명 검증
    if (!SignatureUtil.verifySignature(timestamp, securedApiProperties.getSecretKey(), signature)) {
      log.warn("SecuredApi 서명 불일치 - URI: {}", request.getRequestURI());
      throw new CustomException(ErrorCode.INVALID_SIGNATURE);
    }

    log.debug("SecuredApi 검증 통과 - URI: {}", request.getRequestURI());
    return joinPoint.proceed();
  }

  private HttpServletRequest getCurrentHttpRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    return attributes.getRequest();
  }
}
