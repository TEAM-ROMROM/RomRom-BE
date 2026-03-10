package com.romrom.storage.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.storage.dto.CompressedImage;
import com.romrom.storage.dto.StorageRequest;
import com.romrom.storage.dto.StorageResponse;
import com.romrom.storage.util.FileUtil;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class StorageService {

  private final MinIoFileServiceImpl minioService;
  private final FtpFileServiceImpl ftpService;
  private final ImageCompressionService imageCompressionService;

  public StorageService(
      MinIoFileServiceImpl minioService,
      FtpFileServiceImpl ftpService,
      ImageCompressionService imageCompressionService
  ) {
    this.minioService = minioService;
    this.ftpService = ftpService;
    this.imageCompressionService = imageCompressionService;
  }

  @Transactional
  public StorageResponse saveImages(StorageRequest request) {
    List<MultipartFile> itemImageFiles = request.getImages();
    List<String> imageUrls = new ArrayList<>();

    for (MultipartFile file : itemImageFiles) {
      String imageUrl = uploadWithFallback(file);
      imageUrls.add(imageUrl);
    }
    log.debug("파일 업로드 요청 완료: fileCount={}", imageUrls.size());

    return StorageResponse.builder()
        .imageUrls(imageUrls)
        .build();
  }

  @Transactional
  public void deleteImages(StorageRequest request) {
    List<String> imageUrls = request.getImageUrls();

    for (String imageUrl : imageUrls) {
      deleteByUrl(imageUrl);
    }
    log.debug("파일 삭제 요청 완료: fileCount={}", imageUrls.size());
  }

  /**
   * MinIO 1순위 업로드, 실패 시 FTP fallback
   * 압축은 여기서 한 번만 수행
   */
  private String uploadWithFallback(MultipartFile file) {
    FileUtil.validateFile(file);

    // 1. 이미지 압축 (공통)
    CompressedImage compressed = imageCompressionService.compress(file);

    // 2. MinIO 시도
    try {
      if (compressed != null) {
        String filePath = uploadCompressed(minioService, compressed);
        return minioService.buildImageUrl(filePath);
      } else {
        String filePath = uploadOriginal(minioService, file);
        return minioService.buildImageUrl(filePath);
      }
    } catch (Exception e) {
      log.warn("MinIO 업로드 실패, FTP fallback 전환: {}", file.getOriginalFilename(), e);
    }

    // 3. FTP fallback
    try {
      if (compressed != null) {
        String filePath = uploadCompressed(ftpService, compressed);
        return ftpService.buildImageUrl(filePath);
      } else {
        String filePath = uploadOriginal(ftpService, file);
        return ftpService.buildImageUrl(filePath);
      }
    } catch (Exception e) {
      log.error("FTP fallback 업로드도 실패: {}", file.getOriginalFilename(), e);
      throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
    }
  }

  /**
   * 압축된 이미지를 FileService 구현체로 업로드
   */
  private String uploadCompressed(FileService service, CompressedImage compressed) {
    String fileName = FileUtil.generateFilenameFromString(compressed.getFileName());
    try (InputStream inputStream = new ByteArrayInputStream(compressed.getData())) {
      return service.uploadFile(fileName, inputStream, compressed.getCompressedSize(),
          compressed.getContentType());
    } catch (Exception e) {
      throw new RuntimeException("압축 이미지 업로드 실패: " + compressed.getFileName(), e);
    }
  }

  /**
   * 원본 파일을 FileService 구현체로 업로드
   */
  private String uploadOriginal(FileService service, MultipartFile file) {
    String fileName = FileUtil.generateFilename(file);
    try (InputStream inputStream = file.getInputStream()) {
      return service.uploadFile(fileName, inputStream, file.getSize(), file.getContentType());
    } catch (Exception e) {
      throw new RuntimeException("원본 파일 업로드 실패: " + file.getOriginalFilename(), e);
    }
  }

  /**
   * URL 형식으로 MinIO인지 FTP인지 판별하여 삭제
   */
  private void deleteByUrl(String imageUrl) {
    // MinIO URL인지 확인
    String minioPath = minioService.extractFilePath(imageUrl);
    if (minioPath != null) {
      minioService.deleteFile(minioPath);
      return;
    }

    // FTP URL인지 확인
    String ftpPath = ftpService.extractFilePath(imageUrl);
    if (ftpPath != null) {
      ftpService.deleteFile(ftpPath);
      return;
    }

    log.warn("알 수 없는 이미지 URL 형식, 삭제 건너뜀: {}", imageUrl);
  }
}
