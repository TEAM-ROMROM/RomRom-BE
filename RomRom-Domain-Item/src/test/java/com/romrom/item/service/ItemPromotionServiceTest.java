package com.romrom.item.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.common.constant.ItemStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemPromotionHistory;
import com.romrom.item.repository.postgres.ItemPromotionHistoryRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 롬업 활성화 한도 단위 테스트 (#819): 물품 24시간 1회, 회원 하루 3회, 소유자·상태 검증
 */
@ExtendWith(MockitoExtension.class)
class ItemPromotionServiceTest {

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

  private ItemRequest createPromoteRequest(Member requestMember, UUID itemId) {
    ItemRequest promoteRequest = new ItemRequest();
    promoteRequest.setMember(requestMember);
    promoteRequest.setItemId(itemId);
    return promoteRequest;
  }

  private ErrorCode activateAndCaptureErrorCode(ItemRequest promoteRequest) {
    CustomException customException = Assertions.assertThrows(CustomException.class,
        () -> itemPromotionService.activatePromotion(promoteRequest));
    return customException.getErrorCode();
  }

  @Test
  @DisplayName("한도 안이면 이력 저장 + 만료 일시 24시간 뒤")
  void activatePromotion_성공_만료24시간() {
    when(itemRepository.findNotDeletedItemByItemIdForUpdate(ownerItem.getItemId())).thenReturn(Optional.of(ownerItem));
    when(itemPromotionHistoryRepository.existsByItemItemIdAndPromotionStartedAtAfter(eq(ownerItem.getItemId()), any()))
        .thenReturn(false);
    when(itemPromotionHistoryRepository.countByMemberMemberIdAndPromotionStartedAtGreaterThanEqual(
        eq(ownerMember.getMemberId()), any())).thenReturn(2L);
    when(itemRepository.save(ownerItem)).thenReturn(ownerItem);

    LocalDateTime beforeActivation = LocalDateTime.now();
    ItemResponse promoteResponse = itemPromotionService.activatePromotion(
        createPromoteRequest(ownerMember, ownerItem.getItemId()));

    ArgumentCaptor<ItemPromotionHistory> historyCaptor = ArgumentCaptor.forClass(ItemPromotionHistory.class);
    verify(itemPromotionHistoryRepository).save(historyCaptor.capture());
    ItemPromotionHistory savedHistory = historyCaptor.getValue();
    Assertions.assertEquals(ItemPromotionService.PROMOTION_DURATION_HOURS,
        ChronoUnit.HOURS.between(savedHistory.getPromotionStartedAt(), savedHistory.getPromotionExpiresAt()));
    Assertions.assertFalse(savedHistory.getPromotionStartedAt().isBefore(beforeActivation));
    Assertions.assertEquals(savedHistory.getPromotionExpiresAt(), promoteResponse.getItem().getPromotionExpiresAt());
    Assertions.assertTrue(promoteResponse.getItem().getIsPromoted());
  }

  @Test
  @DisplayName("같은 물품을 24시간 안에 다시 활성화하면 ITEM_PROMOTION_ALREADY_ACTIVE")
  void activatePromotion_물품24시간한도() {
    when(itemRepository.findNotDeletedItemByItemIdForUpdate(ownerItem.getItemId())).thenReturn(Optional.of(ownerItem));
    ArgumentCaptor<LocalDateTime> cooldownStartCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    when(itemPromotionHistoryRepository.existsByItemItemIdAndPromotionStartedAtAfter(
        eq(ownerItem.getItemId()), cooldownStartCaptor.capture())).thenReturn(true);

    LocalDateTime beforeActivation = LocalDateTime.now();
    ErrorCode errorCode = activateAndCaptureErrorCode(createPromoteRequest(ownerMember, ownerItem.getItemId()));

    Assertions.assertEquals(ErrorCode.ITEM_PROMOTION_ALREADY_ACTIVE, errorCode);
    // 판정 기준 = 지금 - 24시간
    long cooldownHours = ChronoUnit.HOURS.between(cooldownStartCaptor.getValue(), beforeActivation.plusMinutes(1));
    Assertions.assertEquals(ItemPromotionService.ITEM_PROMOTION_COOLDOWN_HOURS, cooldownHours);
    verify(itemPromotionHistoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("회원이 오늘 3회 사용했으면 ITEM_PROMOTION_DAILY_LIMIT_EXCEEDED")
  void activatePromotion_회원하루3회한도() {
    when(itemRepository.findNotDeletedItemByItemIdForUpdate(ownerItem.getItemId())).thenReturn(Optional.of(ownerItem));
    when(itemPromotionHistoryRepository.existsByItemItemIdAndPromotionStartedAtAfter(eq(ownerItem.getItemId()), any()))
        .thenReturn(false);
    when(itemPromotionHistoryRepository.countByMemberMemberIdAndPromotionStartedAtGreaterThanEqual(
        eq(ownerMember.getMemberId()), any())).thenReturn(ItemPromotionService.MEMBER_DAILY_PROMOTION_LIMIT);

    ErrorCode errorCode = activateAndCaptureErrorCode(createPromoteRequest(ownerMember, ownerItem.getItemId()));

    Assertions.assertEquals(ErrorCode.ITEM_PROMOTION_DAILY_LIMIT_EXCEEDED, errorCode);
    verify(itemPromotionHistoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("본인 물품이 아니면 INVALID_ITEM_OWNER")
  void activatePromotion_소유자아님() {
    when(itemRepository.findNotDeletedItemByItemIdForUpdate(ownerItem.getItemId())).thenReturn(Optional.of(ownerItem));
    Member otherMember = Member.builder().memberId(UUID.randomUUID()).build();

    ErrorCode errorCode = activateAndCaptureErrorCode(createPromoteRequest(otherMember, ownerItem.getItemId()));

    Assertions.assertEquals(ErrorCode.INVALID_ITEM_OWNER, errorCode);
  }

  @Test
  @DisplayName("교환 완료 물품은 ITEM_NOT_PROMOTABLE")
  void activatePromotion_교환가능아님() {
    ownerItem.setItemStatus(ItemStatus.EXCHANGED);
    when(itemRepository.findNotDeletedItemByItemIdForUpdate(ownerItem.getItemId())).thenReturn(Optional.of(ownerItem));

    ErrorCode errorCode = activateAndCaptureErrorCode(createPromoteRequest(ownerMember, ownerItem.getItemId()));

    Assertions.assertEquals(ErrorCode.ITEM_NOT_PROMOTABLE, errorCode);
  }

  @Test
  @DisplayName("없는 물품은 ITEM_NOT_FOUND")
  void activatePromotion_물품없음() {
    UUID missingItemId = UUID.randomUUID();
    when(itemRepository.findNotDeletedItemByItemIdForUpdate(missingItemId)).thenReturn(Optional.empty());

    ErrorCode errorCode = activateAndCaptureErrorCode(createPromoteRequest(ownerMember, missingItemId));

    Assertions.assertEquals(ErrorCode.ITEM_NOT_FOUND, errorCode);
  }

  @Test
  @DisplayName("하루 한도 기준은 KST 0시 (UTC 서버에서도 KST 날짜로 계산)")
  void toPromotionDayStart_KST기준() {
    ZoneId utcZone = ZoneId.of("UTC");
    // UTC 2026-10-31 16:30 = KST 2026-11-01 01:30 > KST 11-01 00:00 = UTC 10-31 15:00
    LocalDateTime utcServerNow = LocalDateTime.of(2026, 10, 31, 16, 30);
    Assertions.assertEquals(LocalDateTime.of(2026, 10, 31, 15, 0),
        ItemPromotionService.toPromotionDayStart(utcServerNow, utcZone));

    // KST 서버: 2026-11-01 01:30 > 2026-11-01 00:00
    ZoneId kstZone = ZoneId.of("Asia/Seoul");
    Assertions.assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0),
        ItemPromotionService.toPromotionDayStart(LocalDateTime.of(2026, 11, 1, 1, 30), kstZone));
  }
}
