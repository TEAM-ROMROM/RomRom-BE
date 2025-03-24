package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.mongo.CustomTags;
import com.romrom.romback.domain.repository.mongo.CustomTagsRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomTagsService {

  @Value("${custom.tag.max-count}")
  private int maxTagCount;

  @Value("${custom.tag.max-length}")
  private int maxTagLength;

  private final CustomTagsRepository customTagsRepository;

  /**
   * 특정 itemId 문서를 찾고, 없으면 새로 만들고,
   * tags 배열에 새 태그들을 추가한다.

   * 모든 아이템은 하나의 itemCustomTags 도큐먼트가 존재,
   * 태그가 없으면 도큐먼트의 List<String> tags 가 빈 리스트로 존재

   * 태그 생성, 수정, 삭제 -> 문자열 List 그대로 덮어 씌우기
   * 로직은 같으니 굳이 만들필요가 없을듯 해서 이 메서드로 작업 가능
   */
  public List<String> updateTags(UUID itemId, List<String> customTags) {
    CustomTags doc = customTagsRepository.findByItemId(itemId)
        // 업데이트 시 커스텀 태그가 없으면, 즉 처음 등록이면
        // 새로운 커스텀 태그 객체 반환
        .orElseGet(() -> CustomTags.builder()
            .itemId(itemId)
            .customTags(customTags)
            .build());

    // 커스텀 태그 최대 개수 예외 처리
    if(doc.getCustomTags().size() > maxTagCount) {
      throw new CustomException(ErrorCode.TOO_MANY_CUSTOM_TAGS);
    }

    // 커스텀 태그 최대 길이 예외 처리
    if(doc.getCustomTags().stream().anyMatch(s -> s.length() > maxTagLength)) {
      throw new CustomException(ErrorCode.TOO_LONG_CUSTOM_TAGS);
    }

    // 커스텀 태그 업데이트
    doc.updateTags(customTags);

    // 커스텀 태그 저장
    return customTagsRepository.save(doc).getCustomTags();
  }

  /**
   * itemId에 해당하는 태그 리스트 조회
   */
  public List<String> getTags(UUID itemId) {
    return customTagsRepository.findByItemId(itemId)
        .map(CustomTags::getCustomTags)
        .orElse(List.of());
  }
  /**
   * item 삭제시 ItemId에 해당하는 커스텀태그도 같이 삭제
   */
  public void deleteTagsWithItem(UUID itemId) {
    customTagsRepository.deleteByItemId(itemId);
  }
}