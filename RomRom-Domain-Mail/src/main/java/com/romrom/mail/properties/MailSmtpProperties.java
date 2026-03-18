package com.romrom.mail.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "mail.smtp")
public class MailSmtpProperties {
  private String host;
  private int port;
  private String username;
  private String password;
}
