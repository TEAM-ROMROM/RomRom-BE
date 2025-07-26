package com.romrom.web.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class FirebaseConfig {
  @Value("${firebase.service-account-key-path}")
  private String firebaseKeyPath;

  @PostConstruct
  public void init() throws IOException {
    try {
      // resources 디렉토리에 있는 서비스 계정 키 파일 로드
      InputStream serviceAccount = getClass()
          .getResourceAsStream(firebaseKeyPath);

      FirebaseOptions options = FirebaseOptions.builder()
          .setCredentials(GoogleCredentials.fromStream(serviceAccount))
          .build();

      // 이미 FirebaseApp이 초기화되어 있지 않았다면 초기화
      if (FirebaseApp.getApps().isEmpty()) {
        FirebaseApp.initializeApp(options);
        log.debug("FireBase 설정 초기화 완료");
      }
    } catch (IOException e) {
      log.error("Firebase 초기화 중 오류가 발생했습니다: {}", e.getMessage());
      throw e;
    }
  }
}
