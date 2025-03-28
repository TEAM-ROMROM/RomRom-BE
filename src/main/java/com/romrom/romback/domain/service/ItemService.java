package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.dto.ItemRequest;
import com.romrom.romback.domain.object.dto.ItemResponse;
import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.ItemImage;
import com.romrom.romback.domain.repository.postgres.ItemImageRepository;
import com.romrom.romback.domain.repository.postgres.ItemRepository;
import com.romrom.romback.global.SmbService;
import java.io.File;
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
public class ItemService {

  private final ItemRepository itemRepository;
  private final ItemImageRepository itemImageRepository;
  private final SmbService smbService;
  private final ItemCustomTagsService itemCustomTagsService;

  @Transactional
  public ItemResponse postItem(ItemRequest request) {
    Item item = Item.builder()
        .member(request.getMember())
        .itemName(request.getItemName())
        .itemDescription(request.getItemDescription())
        .itemCategory(request.getItemCategory())
        .itemCondition(request.getItemCondition())
        .itemTradeOptions(request.getItemTradeOptions())
        .price(request.getItemPrice())
        .build();
    itemRepository.save(item);

    // 커스텀 태그 서비스 코드 추가
    List<String> customTags = itemCustomTagsService.updateTags(item.getItemId(), request.getItemCustomTags());

    List<ItemImage> itemImages = new ArrayList<>();
    try {
      List<String> uploadedFilePaths = smbService.uploadFile(request.getItemImages()).join();
      for (int i = 0; i < uploadedFilePaths.size(); i++) {
        String filePath = uploadedFilePaths.get(i);
        MultipartFile file = request.getItemImages().get(i);

        ItemImage itemImage = ItemImage.builder()
            .item(item)
            .imageUrl(filePath)
            .originalFileName(file.getOriginalFilename())
            .uploadedFileName(new File(filePath).getName())
            .fileSize(file.getSize())
            .build();
        itemImages.add(itemImage);
      }
      itemImageRepository.saveAll(itemImages);
      log.info("물품 등록 완료: itemId={}, 업로드된 파일 수={}", item.getItemId(), itemImages.size());
    } catch (Exception e) {
      log.error("물품 사진 업로드 실패: 오류={}", e.getMessage());
      throw new RuntimeException("물품 사진 업로드 중 오류 발생", e);
    }

    return ItemResponse.builder()
        .member(request.getMember())
        .item(item)
        .itemImages(itemImages)
        .itemCustomTags(customTags)
        .build();
  }
}
