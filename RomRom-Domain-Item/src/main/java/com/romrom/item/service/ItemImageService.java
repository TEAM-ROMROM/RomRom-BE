package com.romrom.item.service;

import com.romrom.common.service.FtpService;
import com.romrom.common.service.SmbService;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.repository.postgres.ItemImageRepository;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemImageService {

  @Value("${ftp.host}")
  private String baseUrl;

  private final ItemImageRepository itemImageRepository;
  private final SmbService smbService;
  private final FtpService ftpService;

  // 물품 사진 저장
  @Transactional
  public List<ItemImage> saveItemImages(Item item, List<MultipartFile> itemImageFiles) {
    List<ItemImage> itemImages = new ArrayList<>();
    try {
      // FTP 이미지 업로드
      List<String> uploadedFilePaths = ftpService.uploadFile(itemImageFiles).join();
      for (int i = 0; i < uploadedFilePaths.size(); i++) {
        String filePath = uploadedFilePaths.get(i);
        MultipartFile file = itemImageFiles.get(i);

        // ItemImage 생성
        ItemImage itemImage = ItemImage.builder()
          .item(item)
          .imageUrl(baseUrl + filePath)
          .filePath(filePath)
          .originalFileName(file.getOriginalFilename())
          .uploadedFileName(new File(filePath).getName())
          .fileSize(file.getSize())
          .build();
        itemImages.add(itemImage);
      }
      // ItemImages 저장
      itemImageRepository.saveAll(itemImages);
      log.info("물품 사진 저장 완료: itemId={}, 업로드된 파일 수={}", item.getItemId(), itemImages.size());
      return itemImages;
    } catch (Exception e) {
      log.error("물품 사진 업로드 실패: {}", e.getMessage());
      throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
    }
  }

  /**
   * 아이템 삭제 시 연관된 이미지 파일을 FTP 서버에서 삭제 요청하고,
   * DB 레코드를 제거합니다.
   *
   * @param item 삭제 대상 Item 엔티티
   */
  @Transactional
  public void deleteItemImages(Item item) {

    // 1) DB 에서 해당 아이템의 이미지 레코드 조회
    List<ItemImage> images = itemImageRepository.findByItem(item);
    if (images.isEmpty()) {
      log.warn("삭제할 이미지가 없습니다: itemId={}", item.getItemId());
      return;
    }

    // 2) FTP 서버에 삭제 요청
    List<String> filePaths = images.stream()
      .map(ItemImage::getFilePath)
      .collect(Collectors.toList());

    ftpService.deleteFile(filePaths);
    log.info("FTP 파일 삭제 요청 완료: fileCount={}", filePaths.size());

    // 3) DB 레코드 삭제
    itemImageRepository.deleteAll(images);
    log.info("DB 물품 사진 레코드 삭제 완료: itemId={}, 삭제건수={}", item.getItemId(), images.size());
  }
}