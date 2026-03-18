package com.romrom.web.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.entity.postgres.SystemConfig;
import com.romrom.common.repository.SystemConfigRepository;
import com.romrom.mail.service.MailService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAlertConfigService {

  private static final String CONFIG_KEY_ALERT_EMAIL = "admin.alert.email";
  private static final String CONFIG_KEY_THROTTLE_MINUTES = "admin.alert.throttle.minutes";
  private static final String CONFIG_KEY_SMTP_HOST = "mail.smtp.host";
  private static final String CONFIG_KEY_SMTP_PORT = "mail.smtp.port";
  private static final String CONFIG_KEY_SMTP_USERNAME = "mail.smtp.username";
  private static final String CONFIG_KEY_SMTP_PASSWORD = "mail.smtp.password";

  private static final int DEFAULT_THROTTLE_MINUTES = 30;
  private static final int DEFAULT_SMTP_PORT = 587;

  private final SystemConfigRepository systemConfigRepository;
  private final SystemConfigCacheService systemConfigCacheService;
  private final MailService mailService;

  @Value("${romrom.alert.email:}")
  private String defaultAlertEmail;

  @Value("${romrom.alert.throttle-minutes:30}")
  private String defaultThrottleMinutes;

  @Value("${romrom.mail.host:smtp.gmail.com}")
  private String defaultSmtpHost;

  @Value("${romrom.mail.port:587}")
  private String defaultSmtpPort;

  @Value("${romrom.mail.username:}")
  private String defaultSmtpUsername;

  @Value("${romrom.mail.password:}")
  private String defaultSmtpPassword;

  /**
   * 알림/SMTP 설정 초기화 (SystemConfigService.onApplicationReady()에서 호출)
   * - DB에 키가 없으면 application-prod.yml 기본값으로 INSERT
   * - DB/yml 값으로 JavaMailSender 초기화
   */
  public void initializeAlertConfig() {
    Map<String, String> defaultAlertConfigMap = new LinkedHashMap<>();
    defaultAlertConfigMap.put(CONFIG_KEY_ALERT_EMAIL, nullToEmpty(defaultAlertEmail));
    defaultAlertConfigMap.put(CONFIG_KEY_THROTTLE_MINUTES, nullToEmpty(defaultThrottleMinutes));
    defaultAlertConfigMap.put(CONFIG_KEY_SMTP_HOST, nullToEmpty(defaultSmtpHost));
    defaultAlertConfigMap.put(CONFIG_KEY_SMTP_PORT, nullToEmpty(defaultSmtpPort));
    defaultAlertConfigMap.put(CONFIG_KEY_SMTP_USERNAME, nullToEmpty(defaultSmtpUsername));
    defaultAlertConfigMap.put(CONFIG_KEY_SMTP_PASSWORD, nullToEmpty(defaultSmtpPassword));

    for (Map.Entry<String, String> configEntry : defaultAlertConfigMap.entrySet()) {
      String configKey = configEntry.getKey();
      String ymlDefaultValue = configEntry.getValue();

      if (systemConfigRepository.findByConfigKey(configKey).isEmpty()) {
        SystemConfig newAlertConfig = SystemConfig.builder()
            .configKey(configKey)
            .configValue(ymlDefaultValue)
            .description(getConfigDescription(configKey))
            .build();
        systemConfigRepository.save(newAlertConfig);
        systemConfigCacheService.put(configKey, ymlDefaultValue);
        log.info("알림 설정 초기화 (DB INSERT): {}={}", configKey, configKey.contains("password") ? "***" : ymlDefaultValue);
      }
    }

    // JavaMailSender 초기화
    refreshMailSender();
    log.info("관리자 알림 설정 초기화 완료");
  }

  /**
   * 알림/SMTP 설정 조회
   */
  public AdminResponse getAlertConfig() {
    return AdminResponse.builder()
        .alertEmail(systemConfigCacheService.getOrDefault(CONFIG_KEY_ALERT_EMAIL, ""))
        .alertThrottleMinutes(
            parseIntOrDefault(systemConfigCacheService.getOrDefault(CONFIG_KEY_THROTTLE_MINUTES, "30"), DEFAULT_THROTTLE_MINUTES)
        )
        .mailSmtpHost(systemConfigCacheService.getOrDefault(CONFIG_KEY_SMTP_HOST, "smtp.gmail.com"))
        .mailSmtpPort(
            parseIntOrDefault(systemConfigCacheService.getOrDefault(CONFIG_KEY_SMTP_PORT, "587"), DEFAULT_SMTP_PORT)
        )
        .mailSmtpUsername(systemConfigCacheService.getOrDefault(CONFIG_KEY_SMTP_USERNAME, ""))
        .build();
  }

  /**
   * 알림/SMTP 설정 수정
   */
  @Transactional
  public AdminResponse updateAlertConfig(AdminRequest adminRequest) {
    updateConfigIfPresent(CONFIG_KEY_ALERT_EMAIL, adminRequest.getAlertEmail());
    updateConfigIfPresent(CONFIG_KEY_THROTTLE_MINUTES,
        adminRequest.getAlertThrottleMinutes() != null ? String.valueOf(adminRequest.getAlertThrottleMinutes()) : null);
    updateConfigIfPresent(CONFIG_KEY_SMTP_HOST, adminRequest.getMailSmtpHost());
    updateConfigIfPresent(CONFIG_KEY_SMTP_PORT,
        adminRequest.getMailSmtpPort() != null ? String.valueOf(adminRequest.getMailSmtpPort()) : null);
    updateConfigIfPresent(CONFIG_KEY_SMTP_USERNAME, adminRequest.getMailSmtpUsername());
    updateConfigIfPresent(CONFIG_KEY_SMTP_PASSWORD, adminRequest.getMailSmtpPassword());

    // SMTP 설정이 변경됐을 수 있으므로 JavaMailSender 재생성
    refreshMailSender();

    log.info("관리자 알림 설정 업데이트 완료");
    return getAlertConfig();
  }

  private void updateConfigIfPresent(String configKey, String configValue) {
    if (configValue == null) {
      return;
    }

    SystemConfig alertConfig = systemConfigRepository.findByConfigKey(configKey)
        .orElseGet(() -> SystemConfig.builder()
            .configKey(configKey)
            .description(getConfigDescription(configKey))
            .build());
    alertConfig.setConfigValue(configValue);
    systemConfigRepository.save(alertConfig);
    systemConfigCacheService.put(configKey, configValue);
  }

  private void refreshMailSender() {
    String smtpHost = systemConfigCacheService.getOrDefault(CONFIG_KEY_SMTP_HOST, "smtp.gmail.com");
    int smtpPort = parseIntOrDefault(systemConfigCacheService.getOrDefault(CONFIG_KEY_SMTP_PORT, "587"), DEFAULT_SMTP_PORT);
    String smtpUsername = systemConfigCacheService.getOrDefault(CONFIG_KEY_SMTP_USERNAME, "");
    String smtpPassword = systemConfigCacheService.getOrDefault(CONFIG_KEY_SMTP_PASSWORD, "");

    mailService.updateMailSender(smtpHost, smtpPort, smtpUsername, smtpPassword);
  }

  private int parseIntOrDefault(String value, int defaultValue) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException numberFormatException) {
      log.warn("숫자 파싱 실패, 기본값 사용: value={}, default={}", value, defaultValue);
      return defaultValue;
    }
  }

  private String nullToEmpty(String value) {
    return value != null ? value : "";
  }

  private String getConfigDescription(String configKey) {
    return switch (configKey) {
      case CONFIG_KEY_ALERT_EMAIL -> "관리자 알림 수신 이메일";
      case CONFIG_KEY_THROTTLE_MINUTES -> "신고 알림 쓰로틀링 (분)";
      case CONFIG_KEY_SMTP_HOST -> "SMTP 서버 호스트";
      case CONFIG_KEY_SMTP_PORT -> "SMTP 서버 포트";
      case CONFIG_KEY_SMTP_USERNAME -> "SMTP 발송 계정";
      case CONFIG_KEY_SMTP_PASSWORD -> "SMTP 발송 비밀번호";
      default -> configKey;
    };
  }
}
