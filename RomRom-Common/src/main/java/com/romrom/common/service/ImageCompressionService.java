package com.romrom.common.service;

import com.romrom.common.dto.CompressedImage;
import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 압축 서비스
 * - WebP 포맷으로 이미지를 압축 변환
 * - 리사이징을 통한 용량 최적화
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageCompressionService {

  private static final int TARGET_WIDTH = 1280;  // 최대 너비
  private static final int QUALITY = 80;         // 압축 품질 (0-100)
  private static final String WEBP_EXTENSION = ".webp";
  private static final String WEBP_CONTENT_TYPE = "image/webp";

  /**
   * 이미지를 WebP로 압축 변환
   *
   * @param file 원본 MultipartFile
   * @return 압축된 이미지 정보 (실패 시 null 반환)
   */
  public CompressedImage compress(MultipartFile file) {
    try {
      long originalSize = file.getSize();

      // 이미지 읽기
      BufferedImage originalImage = ImageIO.read(file.getInputStream());
      if (originalImage == null) {
        log.warn("이미지 읽기 실패: {}", file.getOriginalFilename());
        return null;
      }

      // ImmutableImage로 변환
      ImmutableImage image = ImmutableImage.fromAwt(originalImage);

      // 가로 기준 리사이즈 (비율 유지, 큰 이미지만)
      if (image.width > TARGET_WIDTH) {
        image = image.scaleToWidth(TARGET_WIDTH);
        log.debug("이미지 리사이즈: {} -> {}", originalImage.getWidth(), TARGET_WIDTH);
      }

      // WebP로 변환
      byte[] compressedBytes = image.bytes(WebpWriter.DEFAULT.withQ(QUALITY));

      // 파일명 변경 (.webp 확장자)
      String newFileName = changeExtensionToWebp(file.getOriginalFilename());

      double compressionRate = (1 - (double) compressedBytes.length / originalSize) * 100;
      log.info("이미지 압축 완료: {} -> {}, 원본 크기: {} bytes, 압축 후 크기: {} bytes, 압축률: {}%",
          file.getOriginalFilename(), newFileName, originalSize, compressedBytes.length,
          String.format("%.1f", compressionRate));

      return CompressedImage.builder()
          .data(compressedBytes)
          .fileName(newFileName)
          .contentType(WEBP_CONTENT_TYPE)
          .originalSize(originalSize)
          .compressedSize(compressedBytes.length)
          .build();

    } catch (Exception e) {
      log.warn("이미지 압축 실패, 원본 사용 예정: {}, 오류: {}", file.getOriginalFilename(), e.getMessage());
      return null;
    }
  }

  /**
   * 파일 확장자를 .webp로 변경
   *
   * @param filename 원본 파일명
   * @return .webp 확장자로 변경된 파일명
   */
  private String changeExtensionToWebp(String filename) {
    if (filename == null || filename.isEmpty()) {
      return "image" + WEBP_EXTENSION;
    }

    int lastDot = filename.lastIndexOf('.');
    if (lastDot > 0) {
      return filename.substring(0, lastDot) + WEBP_EXTENSION;
    }
    return filename + WEBP_EXTENSION;
  }
}
