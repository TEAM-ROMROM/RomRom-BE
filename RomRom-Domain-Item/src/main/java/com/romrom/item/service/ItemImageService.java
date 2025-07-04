package com.romrom.item.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.service.FileService;
import com.romrom.common.util.FileUtil;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.repository.postgres.ItemImageRepository;
import java.io.File;
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
public class ItemImageService {

  @Value("${file.host}")
  private String baseUrl;

  private final ItemImageRepository itemImageRepository;
  private final FileService smbService;

  // 물품 사진 저장
  @Transactional
  public List<ItemImage> saveItemImages(Item item, List<MultipartFile> itemImageFiles) {
    List<ItemImage> itemImages = new ArrayList<>();
    try {
      // 파일 업로드
      for (MultipartFile file : itemImageFiles) {
        String filePath = smbService.uploadFile(file);
        // ItemImage 생성
        ItemImage itemImage = ItemImage.builder()
            .item(item)
            .imageUrl(baseUrl + filePath)
            .filePath(filePath)
            .originalFileName(FileUtil.validateAndExtractFilename(file))
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
}
