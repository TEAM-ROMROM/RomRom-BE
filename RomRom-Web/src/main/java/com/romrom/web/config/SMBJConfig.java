package com.romrom.web.config;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import java.io.IOException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class SMBJConfig {

  @Value("${file.host}")
  private String host;

  @Value("${file.smb.port}")
  private int port;

  @Value("${file.smb.workgroup}")
  private String workgroup;

  @Value("${file.username}")
  private String username;

  @Value("${file.password}")
  private String password;

  @Bean
  public SMBClient smbClient() {
    return new SMBClient();
  }

  @Bean
  public Session smbSession(SMBClient smbClient) throws IOException {
    Connection connection = smbClient.connect(
        host,
        port
    );
    AuthenticationContext auth = new AuthenticationContext(
        username,
        password.toCharArray(),
        workgroup
    );
    return connection.authenticate(auth);
  }
}
