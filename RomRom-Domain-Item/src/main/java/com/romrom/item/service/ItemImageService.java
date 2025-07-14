package com.romrom.item.service;

import com.romrom.common.service.FileService;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.repository.postgres.ItemImageRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemImageService {

  private final ItemImageRepository itemImageRepository;
  private final FileService fileService;

  // 물품 사진 저장
  @Transactional
  public List<ItemImage> saveItemImages(Item item, List<MultipartFile> itemImageFiles) {
    List<ItemImage> itemImages = new ArrayList<>();
    for (MultipartFile file : itemImageFiles) {
      ItemImage itemImage = ItemImage.builder()
          .item(item)
          .imageUrl(fileService.uploadFile(file))
          .build();
      itemImages.add(itemImage);
    }
    return itemImageRepository.saveAll(itemImages);
  }

  /**
   * 아이템 삭제 시 연관된 이미지 파일을 FTP 서버에서 삭제 요청하고,
   * DB 레코드를 제거합니다.
   *
   * @param item 삭제 대상 Item 엔티티
   */
  @Transactional
  public void deleteItemImages(Item item) {
    // todo : 사진 여러장 삭제시 지연 시간 계산 및 성능 개선 리팩토링

    // 1) DB 에서 해당 아이템의 이미지 레코드 조회
    List<ItemImage> itemImages = itemImageRepository.findAllByItem(item);
    if (itemImages.isEmpty()) {
      log.warn("삭제할 이미지가 없습니다: itemId={}", item.getItemId());
      return;
    }

    // 2) 이미지 삭제 요청
    for (ItemImage itemImage : itemImages) {
      fileService.deleteFile(itemImage.getImageUrl());
    }
    log.info("FTP 파일 삭제 요청 완료: fileCount={}", itemImages.size());

    // 3) DB 레코드 삭제
    itemImageRepository.deleteAll(itemImages);
    log.info("DB 물품 사진 레코드 삭제 완료: itemId={}, 삭제건수={}", item.getItemId(), itemImages.size());
  }
}