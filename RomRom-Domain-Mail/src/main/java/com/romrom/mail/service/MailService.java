package com.romrom.mail.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MailService {

  private volatile JavaMailSenderImpl mailSender;

  /**
   * SMTP 설정으로 JavaMailSender 재생성
   */
  public synchronized void updateMailSender(String smtpHost, int smtpPort, String smtpUsername, String smtpPassword) {
    JavaMailSenderImpl newMailSender = new JavaMailSenderImpl();
    newMailSender.setHost(smtpHost);
    newMailSender.setPort(smtpPort);
    newMailSender.setUsername(smtpUsername);
    newMailSender.setPassword(smtpPassword);

    Properties mailProperties = newMailSender.getJavaMailProperties();
    mailProperties.put("mail.transport.protocol", "smtp");
    mailProperties.put("mail.smtp.auth", "true");
    mailProperties.put("mail.smtp.starttls.enable", "true");
    mailProperties.put("mail.smtp.starttls.required", "true");
    mailProperties.put("mail.smtp.connectiontimeout", "5000");
    mailProperties.put("mail.smtp.timeout", "5000");
    mailProperties.put("mail.smtp.writetimeout", "5000");

    this.mailSender = newMailSender;
    log.info("JavaMailSender 재생성 완료: host={}, port={}, username={}", smtpHost, smtpPort, smtpUsername);
  }

  /**
   * HTML 이메일 발송
   */
  public void sendHtmlEmail(String recipientEmail, String subject, String htmlContent) {
    JavaMailSenderImpl currentMailSender = this.mailSender;

    if (currentMailSender == null) {
      log.warn("JavaMailSender가 초기화되지 않았습니다. 이메일 발송을 건너뜁니다.");
      return;
    }

    String senderUsername = currentMailSender.getUsername();
    if (senderUsername == null || senderUsername.isBlank()) {
      log.warn("SMTP 발송 계정이 설정되지 않았습니다. 이메일 발송을 건너뜁니다.");
      return;
    }

    if (recipientEmail == null || recipientEmail.isBlank()) {
      log.warn("수신 이메일이 비어있습니다. 이메일 발송을 건너뜁니다.");
      return;
    }

    try {
      MimeMessage mimeMessage = currentMailSender.createMimeMessage();
      MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
      messageHelper.setFrom(senderUsername);
      messageHelper.setTo(recipientEmail);
      messageHelper.setSubject(subject);
      messageHelper.setText(htmlContent, true);

      currentMailSender.send(mimeMessage);
      log.info("이메일 발송 완료: to={}, subject={}", recipientEmail, subject);
    } catch (MessagingException | MailException mailSendException) {
      log.error("이메일 발송 실패: to={}, subject={}, error={}", recipientEmail, subject, mailSendException.getMessage(), mailSendException);
    }
  }

  /**
   * MailSender 초기화 여부 확인
   */
  public boolean isConfigured() {
    JavaMailSenderImpl currentMailSender = this.mailSender;
    return currentMailSender != null
        && currentMailSender.getUsername() != null
        && !currentMailSender.getUsername().isBlank();
  }
}
