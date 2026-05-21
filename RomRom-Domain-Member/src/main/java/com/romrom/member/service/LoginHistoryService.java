package com.romrom.member.service;

import com.romrom.common.constant.DeviceType;
import com.romrom.common.constant.LoginResult;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.member.entity.mongo.LoginHistory;
import com.romrom.member.repository.mongo.LoginHistoryRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoginHistoryService {

  private final LoginHistoryRepository loginHistoryRepository;

  @Async
  public void record(
      UUID memberId,
      String ipAddress,
      String userAgent,
      DeviceType deviceType,
      SocialPlatform socialPlatform,
      LoginResult loginResult,
      String failReason
  ) {
    if (memberId == null) {
      log.warn("LoginHistory 기록 skip: memberId가 null입니다. loginResult={}, failReason={}", loginResult, failReason);
      return;
    }
    try {
      LoginHistory loginHistory = LoginHistory.builder()
          .memberId(memberId)
          .loginAt(LocalDateTime.now())
          .ipAddress(ipAddress)
          .userAgent(userAgent)
          .deviceType(deviceType)
          .socialPlatform(socialPlatform)
          .loginResult(loginResult)
          .failReason(failReason)
          .build();
      loginHistoryRepository.save(loginHistory);
    } catch (Exception saveFailureException) {
      log.warn("LoginHistory 저장 실패: memberId={}, loginResult={}, error={}",
          memberId, loginResult, saveFailureException.getMessage());
    }
  }

  /**
   * 현재 요청 컨텍스트에서 IP/UA를 추출해 record 호출
   */
  @Async
  public void recordFromCurrentRequest(
      UUID memberId,
      SocialPlatform socialPlatform,
      LoginResult loginResult,
      String failReason
  ) {
    if (memberId == null) {
      log.warn("LoginHistory 기록 skip: memberId가 null입니다. loginResult={}, failReason={}", loginResult, failReason);
      return;
    }
    String clientIpAddress = null;
    String clientUserAgent = null;
    DeviceType resolvedDeviceType = DeviceType.OTHER;
    try {
      RequestAttributes currentRequestAttributes = RequestContextHolder.getRequestAttributes();
      if (currentRequestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
        HttpServletRequest currentHttpRequest = servletRequestAttributes.getRequest();
        clientIpAddress = extractClientIpAddress(currentHttpRequest);
        clientUserAgent = currentHttpRequest.getHeader("User-Agent");
        resolvedDeviceType = resolveDeviceTypeFromUserAgent(clientUserAgent);
      }
    } catch (IllegalStateException requestNotAvailableException) {
      log.warn("LoginHistory 요청 컨텍스트 없음, IP/UA 누락 기록: memberId={}", memberId);
    }
    record(memberId, clientIpAddress, clientUserAgent, resolvedDeviceType, socialPlatform, loginResult, failReason);
  }

  private String extractClientIpAddress(HttpServletRequest httpRequest) {
    String forwardedForHeader = httpRequest.getHeader("X-Forwarded-For");
    if (forwardedForHeader != null && !forwardedForHeader.isBlank()) {
      return forwardedForHeader.split(",")[0].trim();
    }
    String realIpHeader = httpRequest.getHeader("X-Real-IP");
    if (realIpHeader != null && !realIpHeader.isBlank()) {
      return realIpHeader;
    }
    return httpRequest.getRemoteAddr();
  }

  private DeviceType resolveDeviceTypeFromUserAgent(String userAgent) {
    if (userAgent == null) {
      return DeviceType.OTHER;
    }
    String upperCaseUserAgent = userAgent.toUpperCase();
    if (upperCaseUserAgent.contains("ANDROID")) {
      return DeviceType.ANDROID;
    }
    if (upperCaseUserAgent.contains("IPHONE")
        || upperCaseUserAgent.contains("IPAD")
        || upperCaseUserAgent.contains("IOS")) {
      return DeviceType.IOS;
    }
    return DeviceType.OTHER;
  }
}
