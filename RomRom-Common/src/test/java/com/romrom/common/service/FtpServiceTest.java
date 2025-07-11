package com.romrom.common.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.romrom.web.RomBackApplication;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class FtpServiceTest {

  @Autowired
  FtpService ftpService;

  @Test
  void mainTest() throws Exception {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::hangulMockFileUploadTest);
//    timeLog(this::testFileUploadTest);
    lineLog(null);

    lineLog("테스트종료");
  }

  void hangulMockFileUploadTest() {
    // 테스트용 MultipartFile 생성 (dummy image)
    MockMultipartFile mockFile = new MockMultipartFile(
        "file",
        "한글파일이름테스트_hangulFile.jpg",
        "image/jpeg",
        "dummy image content".getBytes()
    );

    String savedPath = ftpService.uploadFile(mockFile);
    lineLog("업로드 결과 경로: " + savedPath);
    assertNotNull(savedPath);
  }

  void testFileUploadTest() throws Exception {
    // src/test/resources/로고.png
    ClassPathResource imageResource = new ClassPathResource("로고.png");
    try (InputStream is = imageResource.getInputStream()) {
      MockMultipartFile mockFile = new MockMultipartFile(
          "file",
          "로고.png",
          "image/png",
          is
      );

      String savedPath = ftpService.uploadFile(mockFile);
      lineLog("실제파일 업로드 결과: " + savedPath);
      assertNotNull(savedPath);
    }
  }
}