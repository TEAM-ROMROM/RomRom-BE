package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.ItemImage;
import com.romrom.romback.domain.repository.postgres.ItemImageRepository;
import com.romrom.romback.global.SmbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemImageService {

  private final ItemImageRepository itemImageRepository;
  private final SmbService smbService;

  // 물품 사진 저장
  @Transactional
  public List<ItemImage> saveItemImages(Item item, List<MultipartFile> itemImageFiles) {
    List<ItemImage> itemImages = new ArrayList<>();
    try {
      List<String> uploadedFilePaths = smbService.uploadFile(itemImageFiles).join();
      for (int i = 0; i < uploadedFilePaths.size(); i++) {
        String filePath = uploadedFilePaths.get(i);
        MultipartFile file = itemImageFiles.get(i);

        ItemImage itemImage = ItemImage.builder()
            .item(item)
            .filePath(filePath)
            .originalFileName(file.getOriginalFilename())
            .uploadedFileName(new File(filePath).getName())
            .fileSize(file.getSize())
            .build();
        itemImages.add(itemImage);
      }
      itemImageRepository.saveAll(itemImages);
      log.info("물품 사진 저장 완료: itemId={}, 업로드된 파일 수={}", item.getItemId(), itemImages.size());
      return itemImages;
    } catch (Exception e) {
      log.error("물품 사진 업로드 실패: {}", e.getMessage());
      throw new RuntimeException("물품 사진 업로드 중 오류 발생", e);
    }
  }
}
