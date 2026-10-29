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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoginHistoryService {

  // 앱이 보내는 플랫폼 헤더 (ANDROID·IOS), 앱 UA(Dart)에는 OS 정보가 없어 우선 사용 (#830)
  static final String CLIENT_PLATFORM_HEADER = "X-Client-Platform";

  private final LoginHistoryRepository loginHistoryRepository;

  // 요청 스레드에서 추출 후 비동기 저장을 프록시로 호출하기 위한 self-injection (#830)
  // (this.record는 @Async가 적용되지 않음)
  @Lazy
  @Autowired
  private LoginHistoryService self;

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
   * 현재 요청에서 IP·UA·기기 추출(요청 스레드) > 비동기 저장
   * 요청 컨텍스트는 요청 스레드에만 있어 @Async 안에서 읽으면 항상 비어 있음 (#830)
   */
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
        resolvedDeviceType = resolveDeviceType(currentHttpRequest.getHeader(CLIENT_PLATFORM_HEADER), clientUserAgent);
      }
    } catch (IllegalStateException requestNotAvailableException) {
      log.warn("LoginHistory 요청 컨텍스트 없음, IP/UA 누락 기록: memberId={}", memberId);
    }
    self.record(memberId, clientIpAddress, clientUserAgent, resolvedDeviceType, socialPlatform, loginResult, failReason);
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

  /**
   * 기기 판별: 플랫폼 헤더(ANDROID·IOS) > User-Agent 키워드 > OTHER
   */
  static DeviceType resolveDeviceType(String clientPlatformHeader, String userAgent) {
    if (clientPlatformHeader != null) {
      String normalizedPlatform = clientPlatformHeader.trim().toUpperCase();
      if ("ANDROID".equals(normalizedPlatform)) {
        return DeviceType.ANDROID;
      }
      if ("IOS".equals(normalizedPlatform)) {
        return DeviceType.IOS;
      }
    }
    return resolveDeviceTypeFromUserAgent(userAgent);
  }

  private static DeviceType resolveDeviceTypeFromUserAgent(String userAgent) {
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
