package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.mongo.ItemCustomTags;
import com.romrom.romback.domain.repository.mongo.ItemCustomTagsRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ItemCustomTagsService {

  private final int CUSTOM_TAG_MAX_COUNT = 5;
  private final int CUSTOM_TAG_MAX_LENGTH = 10;

  private final ItemCustomTagsRepository itemCustomTagsRepository;

  /**
   * 특정 itemId 문서를 찾고, 없으면 새로 만들고,
   * tags 배열에 새 태그들을 추가한다.

   * 모든 아이템은 하나의 itemCustomTags 도큐먼트가 존재,
   * 태그가 없으면 도큐먼트의 List<String> tags 가 빈 리스트로 존재

   * 태그 생성, 수정, 삭제 -> 문자열 List 그대로 덮어 씌우기
   * 로직은 같으니 굳이 만들필요가 없을듯 해서 이 메서드로 작업 가능
   */
  public List<String> updateTags(UUID itemId, List<String> customTags) {
    ItemCustomTags itemCustomTags = itemCustomTagsRepository.findByItemId(itemId)
        // 업데이트 시 커스텀 태그가 없으면, 즉 처음 등록이면
        // 새로운 커스텀 태그 객체 반환
        .orElseGet(() -> ItemCustomTags.builder()
            .itemId(itemId)
            .customTags(customTags)
            .build());

    // 커스텀 태그 최대 개수 예외 처리
    if(itemCustomTags.getCustomTags().size() > CUSTOM_TAG_MAX_COUNT) {
      log.error("커스텀 태그 최대 개수 초과 : {}" , itemCustomTags.getCustomTags().size());
      throw new CustomException(ErrorCode.TOO_MANY_CUSTOM_TAGS);
    }

    // 커스텀 태그 최대 길이 예외 처리
    for (String customTag : itemCustomTags.getCustomTags()) {
      if (customTag.length() > CUSTOM_TAG_MAX_LENGTH) {
        log.error("커스텀 태그 최대 길이 초과 : {}", customTag);
        throw new CustomException(ErrorCode.TOO_LONG_CUSTOM_TAGS);
      }
    }


    // 커스텀 태그 중복 제거
    Set<String> deduplicatedCustomTagSet = new HashSet<>(customTags);
    List<String> deduplicatedCustomTagList = new ArrayList<>(deduplicatedCustomTagSet);

    // 커스텀 태그 업데이트
    itemCustomTags.updateTags(deduplicatedCustomTagList);

    // 커스텀 태그 저장
    return itemCustomTagsRepository.save(itemCustomTags).getCustomTags();
  }

  /**
   * itemId에 해당하는 태그 리스트 조회
   */
  public List<String> getTags(UUID itemId) {
    return itemCustomTagsRepository.findByItemId(itemId)
        .map(ItemCustomTags::getCustomTags)
        .orElse(List.of());
  }
  /**
   * item 삭제시 ItemId에 해당하는 커스텀태그도 같이 삭제
   */
  public void deleteTagsWithItem(UUID itemId) {
    itemCustomTagsRepository.deleteByItemId(itemId);
  }
}