package com.romrom.romback.domain.service;


import com.romrom.romback.domain.object.constant.LikeContentType;
import com.romrom.romback.domain.object.dto.LikeRequest;
import com.romrom.romback.domain.object.mongo.LikeHistory;
import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.repository.mongo.LikeHistoryRepository;
import com.romrom.romback.domain.repository.postgres.ItemRepository;
import com.romrom.romback.domain.repository.postgres.MemberRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LikeHistoryService {

  private final LikeHistoryRepository likeHistoryRepository;
  private final ItemRepository itemRepository;

  public String likeOrUnlikeItem(LikeRequest request) {

    Member member = request.getMember();
    Item item = itemRepository.findById(request.getItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    // 본인 게시물에는 좋아요 달 수 없으므로 예외 처리
    if(member.equals(item.getMember())) {
      throw new CustomException(ErrorCode.YOUR_ITEM);
    }

    // 좋아요 존재시 취소 로직
    if (likeHistoryRepository.existsByMemberIdAndItemId(member.getMemberId(), item.getItemId())) {
      likeHistoryRepository.deleteByMemberIdAndItemId(item.getItemId(), member.getMemberId());
      item.decreaseLikeCount();
      itemRepository.save(item);
      return "좋아요가 취소되었습니다.";
    }

    // 좋아요 없으면 등록 로직
    likeHistoryRepository.save(LikeHistory.builder()
            .itemId(item.getItemId())
            .memberId(member.getMemberId())
            .likeContentType(LikeContentType.POST)
            .build());
    item.increaseLikeCount();
    itemRepository.save(item);

    return "좋아요가 등록되었습니다.";
  }

}
