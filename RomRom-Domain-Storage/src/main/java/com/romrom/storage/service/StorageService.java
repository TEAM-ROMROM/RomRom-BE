package com.romrom.storage.service;

import com.romrom.common.service.FileService;
import com.romrom.common.util.FileUtil;
import com.romrom.storage.dto.ImageRequest;
import com.romrom.storage.dto.ImageResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

  @Value("${file.domain}")
  private String domain;

  private final FileService fileService;

  @Transactional
  public ImageResponse saveImages(ImageRequest request) {
    List<MultipartFile> itemImageFiles = request.getImages();
    List<String> imageUrls = new ArrayList<>();

    for (MultipartFile file : itemImageFiles) {
      String filePath = fileService.uploadFile(file);
      String imageUrl = FileUtil.combineBaseAndPath(domain, filePath);
      imageUrls.add(imageUrl);
    }
    log.debug("FTP 파일 업로드 요청 완료: fileCount={}", imageUrls.size());

    return ImageResponse.builder()
        .itemImageUrls(imageUrls)
        .build();
  }

  @Transactional
  public void deleteImages(ImageRequest request) {
    List<String> imageUrls = request.getImageUrls();

    // 파일 삭제
    for (String imageUrl : imageUrls) {
      String filePath = FileUtil.extractFilePath(domain, imageUrl);
      fileService.deleteFile(filePath);
    }
    log.debug("FTP 파일 삭제 요청 완료: fileCount={}", imageUrls.size());
  }
}
