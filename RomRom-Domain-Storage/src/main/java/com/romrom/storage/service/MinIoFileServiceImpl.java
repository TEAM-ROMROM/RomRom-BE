package com.romrom.storage.service;

import com.romrom.storage.properties.MinioProperties;
import com.romrom.storage.util.FileUtil;
import com.romrom.storage.util.MinioUtil;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinIoFileServiceImpl implements FileService {

  private final MinioUtil minioUtil;
  private final MinioProperties minioProperties;

  @Override
  public String uploadFile(String fileName, InputStream data, long size, String contentType) {
    String objectName = MinioUtil.PUBLIC_IMAGES_PATH + fileName;

    log.debug("MinIO 업로드 시작: 파일명={}, 크기={} 바이트", fileName, size);
    minioUtil.uploadFile(minioProperties.getBucket(), objectName, data, size, contentType);
    log.debug("MinIO 업로드 성공: {}", objectName);

    return objectName;
  }

  @Override
  public void deleteFile(String objectName) {
    try {
      minioUtil.deleteFile(minioProperties.getBucket(), objectName);
      log.debug("MinIO 파일 삭제 성공: {}", objectName);
    } catch (Exception e) {
      log.warn("MinIO 파일 삭제 실패: {}", objectName, e);
    }
  }

  @Override
  public String buildImageUrl(String filePath) {
    String baseUrl = FileUtil.removeTrailingSlash(minioProperties.getEndpoint());
    return baseUrl + "/" + minioProperties.getBucket() + "/" + filePath;
  }

  @Override
  public String extractFilePath(String imageUrl) {
    String prefix = FileUtil.removeTrailingSlash(minioProperties.getEndpoint())
        + "/" + minioProperties.getBucket() + "/";
    if (imageUrl.startsWith(prefix)) {
      return imageUrl.substring(prefix.length());
    }
    return null;
  }
}
