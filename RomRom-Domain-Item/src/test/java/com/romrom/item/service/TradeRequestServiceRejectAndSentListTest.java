package com.romrom.item.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.ItemTradeOption;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.repository.EmbeddingRepository;
import com.romrom.item.dto.TradeRequest;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.service.MemberBlockService;
import com.romrom.member.service.MemberLocationVerificationService;
import java.util.ArrayList;
import java.util.List;
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
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * 거래 요청 개선 단위 테스트 (#859): 추가금·메시지 저장, 거절 행 보존, 보낸 목록 거절 포함 분기, 확인 시각, 완료 후 정리
 */
@ExtendWith(MockitoExtension.class)
class TradeRequestServiceRejectAndSentListTest {

  @Mock
  private TradeRequestHistoryRepository tradeRequestHistoryRepository;
  @Mock
  private ItemRepository itemRepository;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private MemberBlockService memberBlockService;
  @Mock
  private TradeRequestContentPolicy tradeRequestContentPolicy;
  @Mock
  private MemberLocationVerificationService memberLocationVerificationService;
  // 정렬 기준·방향 정규화는 실제 동작 사용 (AI 추천 임베딩 조회만 mock)
  @Spy
  private TradeRequestPageSorter tradeRequestPageSorter = new TradeRequestPageSorter(Mockito.mock(EmbeddingRepository.class));
  @Mock
  private ItemReservationService itemReservationService;

  @InjectMocks
  private TradeRequestService tradeRequestService;

  private Member receiverMember;
  private Member senderMember;
  private Item takeItem;
  private Item giveItem;

  @BeforeEach
  void setUp() {
    receiverMember = Member.builder().memberId(UUID.randomUUID()).nickname("받는사람").build();
    senderMember = Member.builder().memberId(UUID.randomUUID()).nickname("보낸사람").build();
    takeItem = buildAvailableItem(receiverMember);
    giveItem = buildAvailableItem(senderMember);
  }

  @Test
  @DisplayName("요청 보내기: 정리된 추가금·메시지를 저장")
  void sendTradeRequest_savesExtraChargeAmountAndMessage() {
    List<ItemTradeOption> extraChargeOptions = List.of(ItemTradeOption.EXTRA_CHARGE);
    TradeRequest request = TradeRequest.builder()
        .member(senderMember)
        .giveItemId(giveItem.getItemId())
        .takeItemId(takeItem.getItemId())
        .itemTradeOptions(extraChargeOptions)
        .extraChargeAmount(5000)
        .requestMessage(" 주말 가능해요 ")
        .build();
    when(itemRepository.findByItemIdAndIsDeletedFalse(giveItem.getItemId())).thenReturn(Optional.of(giveItem));
    when(itemRepository.findByItemIdAndIsDeletedFalse(takeItem.getItemId())).thenReturn(Optional.of(takeItem));
    when(tradeRequestContentPolicy.resolveExtraChargeAmount(extraChargeOptions, 5000)).thenReturn(5000);
    when(tradeRequestContentPolicy.resolveRequestMessage(" 주말 가능해요 ")).thenReturn("주말 가능해요");

    tradeRequestService.sendTradeRequest(request);

    ArgumentCaptor<TradeRequestHistory> savedHistoryCaptor = ArgumentCaptor.forClass(TradeRequestHistory.class);
    verify(tradeRequestHistoryRepository).saveAndFlush(savedHistoryCaptor.capture());
    Assertions.assertEquals(5000, savedHistoryCaptor.getValue().getExtraChargeAmount());
    Assertions.assertEquals("주말 가능해요", savedHistoryCaptor.getValue().getRequestMessage());
    Assertions.assertEquals(TradeStatus.PENDING, savedHistoryCaptor.getValue().getTradeStatus());
  }

  @Test
  @DisplayName("거절: 행 삭제 없이 REJECTED + rejectedAt")
  void rejectTradeRequest_keepsRowAsRejected() {
    TradeRequestHistory pendingHistory = buildHistory(TradeStatus.PENDING);
    when(tradeRequestHistoryRepository.findByTradeRequestHistoryIdWithItems(pendingHistory.getTradeRequestHistoryId()))
        .thenReturn(Optional.of(pendingHistory));

    tradeRequestService.rejectTradeRequest(TradeRequest.builder()
        .member(receiverMember)
        .tradeRequestHistoryId(pendingHistory.getTradeRequestHistoryId())
        .build());

    Assertions.assertEquals(TradeStatus.REJECTED, pendingHistory.getTradeStatus());
    Assertions.assertNotNull(pendingHistory.getRejectedAt());
    verify(tradeRequestHistoryRepository, never()).delete(any());
  }

  @Test
  @DisplayName("보낸 목록: includeRejected 미전송(구버전 앱) > false, true 전송 > true")
  void getSentTradeRequests_passesIncludeRejectedFlag() {
    Page<TradeRequestHistory> emptyHistoryPage = new PageImpl<>(List.of());
    when(itemRepository.findByItemIdAndIsDeletedFalse(giveItem.getItemId())).thenReturn(Optional.of(giveItem));
    when(tradeRequestHistoryRepository.findByGiveItem(eq(giveItem), eq(false), any(Pageable.class)))
        .thenReturn(emptyHistoryPage);
    when(tradeRequestHistoryRepository.findByGiveItem(eq(giveItem), eq(true), any(Pageable.class)))
        .thenReturn(emptyHistoryPage);

    TradeRequest legacyRequest = new TradeRequest();
    legacyRequest.setMember(senderMember);
    legacyRequest.setGiveItemId(giveItem.getItemId());
    tradeRequestService.getSentTradeRequests(legacyRequest);
    verify(tradeRequestHistoryRepository).findByGiveItem(eq(giveItem), eq(false), any(Pageable.class));

    TradeRequest rejectedIncludedRequest = new TradeRequest();
    rejectedIncludedRequest.setMember(senderMember);
    rejectedIncludedRequest.setGiveItemId(giveItem.getItemId());
    rejectedIncludedRequest.setIncludeRejected(true);
    tradeRequestService.getSentTradeRequests(rejectedIncludedRequest);
    verify(tradeRequestHistoryRepository).findByGiveItem(eq(giveItem), eq(true), any(Pageable.class));
  }

  @Test
  @DisplayName("상세 조회: 받은 사람만 확인 시각 기록, 최초 시각 유지")
  void getTradeRequest_marksCheckedAtOnlyForReceiver() {
    TradeRequestHistory pendingHistory = buildHistory(TradeStatus.PENDING);
    when(tradeRequestHistoryRepository.findByTradeRequestHistoryIdWithItems(pendingHistory.getTradeRequestHistoryId()))
        .thenReturn(Optional.of(pendingHistory));

    tradeRequestService.getTradeRequest(TradeRequest.builder()
        .member(senderMember)
        .tradeRequestHistoryId(pendingHistory.getTradeRequestHistoryId())
        .build());
    Assertions.assertNull(pendingHistory.getCheckedAt());

    tradeRequestService.getTradeRequest(TradeRequest.builder()
        .member(receiverMember)
        .tradeRequestHistoryId(pendingHistory.getTradeRequestHistoryId())
        .build());
    Assertions.assertNotNull(pendingHistory.getCheckedAt());
    Assertions.assertFalse(pendingHistory.getIsNew());
  }

  @Test
  @DisplayName("거래 완료: 두 물품 EXCHANGED·예약 해제 + 남은 대기 요청 정리 호출")
  void completeTrade_cleansUpOtherPendingRequests() {
    TradeRequestHistory pendingHistory = buildHistory(TradeStatus.PENDING);
    takeItem.reserve();
    when(tradeRequestHistoryRepository.findById(pendingHistory.getTradeRequestHistoryId()))
        .thenReturn(Optional.of(pendingHistory));

    tradeRequestService.completeTrade(TradeRequest.builder()
        .member(receiverMember)
        .tradeRequestHistoryId(pendingHistory.getTradeRequestHistoryId())
        .build());

    Assertions.assertEquals(TradeStatus.TRADED, pendingHistory.getTradeStatus());
    Assertions.assertEquals(ItemStatus.EXCHANGED, takeItem.getItemStatus());
    Assertions.assertNull(takeItem.getReservedAt());
    verify(itemReservationService).cleanUpAfterTradeCompleted(pendingHistory);
  }

  private Item buildAvailableItem(Member ownerMember) {
    List<ItemImage> itemImages = new ArrayList<>();
    itemImages.add(ItemImage.builder().imageUrl("https://example.com/item.jpg").build());
    return Item.builder()
        .itemId(UUID.randomUUID())
        .member(ownerMember)
        .itemName("물품")
        .itemStatus(ItemStatus.AVAILABLE)
        .itemImages(itemImages)
        .build();
  }

  private TradeRequestHistory buildHistory(TradeStatus tradeStatus) {
    return TradeRequestHistory.builder()
        .tradeRequestHistoryId(UUID.randomUUID())
        .takeItem(takeItem)
        .giveItem(giveItem)
        .tradeStatus(tradeStatus)
        .build();
  }
}
