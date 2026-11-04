package com.romrom.item.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.common.constant.ItemStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.dto.ItemPromotionEligibility;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemPromotionHistory;
import com.romrom.item.repository.postgres.ItemPromotionHistoryRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 롬업 사전 자격 단위 테스트 (#858): 활성화와 같은 기준, 저장 없음
 */
@ExtendWith(MockitoExtension.class)
class ItemPromotionEligibilityTest {

  @Mock
  private ItemRepository itemRepository;

  @Mock
  private ItemPromotionHistoryRepository itemPromotionHistoryRepository;

  @InjectMocks
  private ItemPromotionService itemPromotionService;

  private Member ownerMember;
  private Item ownerItem;

  @BeforeEach
  void setUp() {
    ownerMember = Member.builder().memberId(UUID.randomUUID()).build();
    ownerItem = Item.builder()
        .itemId(UUID.randomUUID())
        .member(ownerMember)
        .itemStatus(ItemStatus.AVAILABLE)
        .isAdminHidden(false)
        .build();
  }

  private ItemPromotionEligibility requestEligibility(Member requestMember) {
    ItemRequest eligibilityRequest = new ItemRequest();
    eligibilityRequest.setMember(requestMember);
    eligibilityRequest.setItemId(ownerItem.getItemId());
    return itemPromotionService.getPromotionEligibility(eligibilityRequest).getPromotionEligibility();
  }

  private void stubTodayPromotionCount(long todayPromotionCount) {
    when(itemPromotionHistoryRepository.countByMemberMemberIdAndPromotionStartedAtGreaterThanEqual(
        eq(ownerMember.getMemberId()), any())).thenReturn(todayPromotionCount);
  }

  @Test
  @DisplayName("이력 없음 + 오늘 1회 사용 > 가능, 남은 2회, 저장 없음")
  void getPromotionEligibility_가능() {
    when(itemRepository.findByItemIdAndIsDeletedFalse(ownerItem.getItemId())).thenReturn(Optional.of(ownerItem));
    when(itemPromotionHistoryRepository.findFirstByItemItemIdOrderByPromotionStartedAtDesc(ownerItem.getItemId()))
        .thenReturn(Optional.empty());
    stubTodayPromotionCount(1L);

    ItemPromotionEligibility eligibility = requestEligibility(ownerMember);

    Assertions.assertTrue(eligibility.getIsEligible());
    Assertions.assertNull(eligibility.getIneligibleReason());
    Assertions.assertEquals(2L, eligibility.getRemainingDailyPromotionCount());
    Assertions.assertNull(eligibility.getNextAvailableAt());
    verify(itemPromotionHistoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("1시간 전 롬업 > ITEM_PROMOTION_ALREADY_ACTIVE, 다음 가능 = 23시간 뒤")
  void getPromotionEligibility_물품24시간제한() {
    LocalDateTime lastPromotionStartedAt = LocalDateTime.now().minusHours(1);
    when(itemRepository.findByItemIdAndIsDeletedFalse(ownerItem.getItemId())).thenReturn(Optional.of(ownerItem));
    when(itemPromotionHistoryRepository.findFirstByItemItemIdOrderByPromotionStartedAtDesc(ownerItem.getItemId()))
        .thenReturn(Optional.of(ItemPromotionHistory.builder().promotionStartedAt(lastPromotionStartedAt).build()));
    stubTodayPromotionCount(1L);

    ItemPromotionEligibility eligibility = requestEligibility(ownerMember);

    Assertions.assertFalse(eligibility.getIsEligible());
    Assertions.assertEquals(ErrorCode.ITEM_PROMOTION_ALREADY_ACTIVE.name(), eligibility.getIneligibleReason());
    Assertions.assertEquals(lastPromotionStartedAt.plusHours(ItemPromotionService.ITEM_PROMOTION_COOLDOWN_HOURS),
        eligibility.getNextAvailableAt());
  }

  @Test
  @DisplayName("오늘 3회 사용 > ITEM_PROMOTION_DAILY_LIMIT_EXCEEDED, 다음 가능 = 하루 초기화 시각")
  void getPromotionEligibility_회원하루한도() {
    when(itemRepository.findByItemIdAndIsDeletedFalse(ownerItem.getItemId())).thenReturn(Optional.of(ownerItem));
    when(itemPromotionHistoryRepository.findFirstByItemItemIdOrderByPromotionStartedAtDesc(ownerItem.getItemId()))
        .thenReturn(Optional.empty());
    stubTodayPromotionCount(ItemPromotionService.MEMBER_DAILY_PROMOTION_LIMIT);

    ItemPromotionEligibility eligibility = requestEligibility(ownerMember);

    Assertions.assertFalse(eligibility.getIsEligible());
    Assertions.assertEquals(ErrorCode.ITEM_PROMOTION_DAILY_LIMIT_EXCEEDED.name(), eligibility.getIneligibleReason());
    Assertions.assertEquals(0L, eligibility.getRemainingDailyPromotionCount());
    Assertions.assertEquals(eligibility.getDailyLimitResetsAt(), eligibility.getNextAvailableAt());
  }

  @Test
  @DisplayName("교환 완료 물품 > ITEM_NOT_PROMOTABLE, 다음 가능 없음")
  void getPromotionEligibility_교환불가() {
    ownerItem.setItemStatus(ItemStatus.EXCHANGED);
    when(itemRepository.findByItemIdAndIsDeletedFalse(ownerItem.getItemId())).thenReturn(Optional.of(ownerItem));
    when(itemPromotionHistoryRepository.findFirstByItemItemIdOrderByPromotionStartedAtDesc(ownerItem.getItemId()))
        .thenReturn(Optional.empty());
    stubTodayPromotionCount(0L);

    ItemPromotionEligibility eligibility = requestEligibility(ownerMember);

    Assertions.assertEquals(ErrorCode.ITEM_NOT_PROMOTABLE.name(), eligibility.getIneligibleReason());
    Assertions.assertNull(eligibility.getNextAvailableAt());
  }

  @Test
  @DisplayName("본인 물품 아님 > INVALID_ITEM_OWNER")
  void getPromotionEligibility_소유자아님() {
    when(itemRepository.findByItemIdAndIsDeletedFalse(ownerItem.getItemId())).thenReturn(Optional.of(ownerItem));

    CustomException customException = Assertions.assertThrows(CustomException.class,
        () -> requestEligibility(Member.builder().memberId(UUID.randomUUID()).build()));

    Assertions.assertEquals(ErrorCode.INVALID_ITEM_OWNER, customException.getErrorCode());
  }
}
