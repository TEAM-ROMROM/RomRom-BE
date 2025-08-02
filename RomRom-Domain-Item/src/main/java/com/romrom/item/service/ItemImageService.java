package com.romrom.item.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.service.FileService;
import com.romrom.common.util.FileUtil;
import com.romrom.item.dto.ItemImageRequest;
import com.romrom.item.dto.ItemImageResponse;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

  @Value("${file.domain}")
  private String domain;

  private final ItemImageRepository itemImageRepository;
  private final ItemRepository itemRepository;
  private final FileService fileService;

  // 물품 사진 저장
  @Transactional
  public ItemImageResponse saveItemImages(ItemImageRequest request) {
    List<MultipartFile> itemImageFiles = request.getItemImages();

    List<String> imageUrls = new ArrayList<>();

    for (MultipartFile file : itemImageFiles) {
      String filePath = fileService.uploadFile(file);
      String imageUrl = FileUtil.combineBaseAndPath(domain, filePath);
      imageUrls.add(imageUrl);
    }

    return ItemImageResponse.builder()
        .itemImageUrls(imageUrls)
        .build();
  }

  @Transactional
  public ItemImageResponse deleteItemImages(ItemImageRequest request) {
    Item item = validateItem(request.getItemId());
    List<String> filePaths = request.getFilePaths();

    // 파일 삭제
    for (String filePath : filePaths) {
      fileService.deleteFile(filePath);
    }

    // 남은 이미지 URL 조회
    List<String> remainingUrls = itemImageRepository
        .findAllByItem(item)
        .stream()
        .map(ItemImage::getImageUrl)
        .collect(Collectors.toList());

    return ItemImageResponse.builder()
        .item(item)
        .itemImageUrls(remainingUrls)
        .build();
  }

  /**
   * 아이템 삭제 시 연관된 이미지 파일을 FTP 서버에서 삭제 요청하고, DB 레코드를 제거합니다.
   *
   * @param item 삭제 대상 Item 엔티티
   */
  @Transactional
  public void deleteAllItemImages(Item item) {
    // todo : 사진 여러장 삭제시 지연 시간 계산 및 성능 개선 리팩토링

    // 1) DB 에서 해당 아이템의 이미지 레코드 조회
    List<ItemImage> itemImages = itemImageRepository.findAllByItem(item);
    if (itemImages.isEmpty()) {
      log.warn("삭제할 이미지가 없습니다: itemId={}", item.getItemId());
      return;
    }

    // 2) 이미지 삭제 요청
    for (ItemImage itemImage : itemImages) {
      fileService.deleteFile(itemImage.getFilePath());
    }
    log.info("FTP 파일 삭제 요청 완료: fileCount={}", itemImages.size());

    // 3) DB 레코드 삭제
    itemImageRepository.deleteAll(itemImages);
    log.info("DB 물품 사진 레코드 삭제 완료: itemId={}, 삭제건수={}", item.getItemId(), itemImages.size());
  }

  private Item validateItem(UUID itemId) {
    return itemRepository.findById(itemId)
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
  }
}