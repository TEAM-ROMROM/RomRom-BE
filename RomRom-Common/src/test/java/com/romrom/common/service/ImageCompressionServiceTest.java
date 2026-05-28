package com.romrom.common.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.superLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.romrom.storage.dto.CompressedImage;
import com.romrom.storage.service.ImageCompressionService;
import com.romrom.web.RomBackApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class ImageCompressionServiceTest {

  @Autowired
  ImageCompressionService imageCompressionService;

  @Test
  public void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::imageCompressionService_압축_테스트);
    lineLog(null);

    lineLog(null);
    timeLog(this::imageCompressionService_WebP_소용량_스킵_테스트);
    lineLog(null);

    lineLog("테스트종료");
  }

  public void imageCompressionService_압축_테스트() {
    // 테스트용 이미지 생성 (100x100 PNG)
    MultipartFile testFile = createTestImage(100, 100, "png");

    lineLog("원본 파일 정보");
    superLog("파일명", testFile.getOriginalFilename());
    superLog("파일 크기", testFile.getSize() + " bytes");
    superLog("Content-Type", testFile.getContentType());

    // 압축 실행
    CompressedImage compressed = imageCompressionService.compress(testFile);

    lineLog("압축 결과");
    if (compressed != null) {
      superLog("압축 파일명", compressed.getFileName());
      superLog("원본 크기", compressed.getOriginalSize() + " bytes");
      superLog("압축 후 크기", compressed.getCompressedSize() + " bytes");
      superLog("Content-Type", compressed.getContentType());

      double compressionRate = (1 - (double) compressed.getCompressedSize() / compressed.getOriginalSize()) * 100;
      superLog("압축률", String.format("%.1f%%", compressionRate));
    } else {
      superLog("결과", "압축 실패 (Windows 환경에서는 정상)");
    }
  }

  public void imageCompressionService_WebP_소용량_스킵_테스트() {
    // image/webp + 소용량 MockMultipartFile (실제 webp 디코드 불필요 — 가드는 contentType/size만 검사)
    byte[] tinyWebpBytes = new byte[1024]; // 1KB
    MultipartFile webpFile = new MockMultipartFile("file", "test.webp", "image/webp", tinyWebpBytes);

    lineLog("WebP 소용량 입력 정보");
    superLog("파일명", webpFile.getOriginalFilename());
    superLog("파일 크기", webpFile.getSize() + " bytes");
    superLog("Content-Type", webpFile.getContentType());

    CompressedImage compressed = imageCompressionService.compress(webpFile);

    if (compressed == null) {
      superLog("결과", "WebP 소용량 → 압축 스킵(null) 정상");
    } else {
      superLog("결과", "스킵 실패 — 압축됨: " + compressed.getFileName());
      throw new IllegalStateException("WebP 소용량은 압축 스킵되어야 함");
    }
  }

  /**
   * 테스트용 이미지 생성
   */
  private MultipartFile createTestImage(int width, int height, String format) {
    try {
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

      // 간단한 그라데이션 그리기
      for (int x = 0; x < width; x++) {
        for (int y = 0; y < height; y++) {
          int rgb = (x * 255 / width) << 16 | (y * 255 / height) << 8 | 128;
          image.setRGB(x, y, rgb);
        }
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write(image, format, baos);
      byte[] imageBytes = baos.toByteArray();

      return new MockMultipartFile(
          "file",
          "test_image." + format,
          "image/" + format,
          imageBytes
      );
    } catch (IOException e) {
      throw new RuntimeException("테스트 이미지 생성 실패", e);
    }
  }
}
