package com.romrom.item.service;

import com.romrom.ai.service.EmbeddingService;
import com.romrom.ai.service.VertexAiClient;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.mongo.LikeHistoryRepository;
import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemServiceTest {

  private ItemRepository itemRepository;
  private ItemCustomTagsService itemCustomTagsService;
  private ItemImageService itemImageService;
  private LikeHistoryRepository likeHistoryRepository;
  private EmbeddingService embeddingService;
  private VertexAiClient vertexAiClient;
  private ItemImageRepository itemImageRepository;
  private MemberRepository memberRepository;
  private TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private ItemService itemService;

  private Member owner;
  private Item item;

  @BeforeEach
  void setUp() {
    itemRepository = mock(ItemRepository.class);
    itemCustomTagsService = mock(ItemCustomTagsService.class);
    itemImageService = mock(ItemImageService.class);
    likeHistoryRepository = mock(LikeHistoryRepository.class);
    embeddingService = mock(EmbeddingService.class);
    vertexAiClient = mock(VertexAiClient.class);
    itemImageRepository = mock(ItemImageRepository.class);
    memberRepository = mock(MemberRepository.class);
    tradeRequestHistoryRepository = mock(TradeRequestHistoryRepository.class);

    // mock()된 의존성은 리턴값이 명시되지 않으면 기본적으로 null을 반환

    itemService = new ItemService(
        itemRepository,
        itemCustomTagsService,
        itemImageService,
        likeHistoryRepository,
        embeddingService,
        vertexAiClient,
        itemImageRepository,
        memberRepository,
        tradeRequestHistoryRepository
    );
    owner = Member.builder().memberId(UUID.randomUUID()).build();

    item = Item.builder()
        .itemId(UUID.randomUUID())
        .member(owner)
        .itemStatus(ItemStatus.AVAILABLE)
        .build();
  }

  @Test
  void 본인_물품의_거래상태를_성공적으로_변경한다() {

    // given
    // mock()된 의존성은 리턴값이 명시되지 않으면 기본적으로 null을 반환
    when(itemRepository.findById(item.getItemId()))
        .thenReturn(Optional.of(item));
    when(itemRepository.save(any(Item.class)))
        .thenReturn(item);
    when(itemImageRepository.findAllByItem(any()))
        .thenReturn(List.of());
    when(itemCustomTagsService.getTags(any()))
        .thenReturn(List.of());
    when(likeHistoryRepository.existsByMemberIdAndItemId(any(), any()))
        .thenReturn(false);

    ItemRequest request = new ItemRequest();
    request.setItemId(item.getItemId());
    request.setMember(owner);
    request.setItemStatus(ItemStatus.EXCHANGED);

    // when
    ItemResponse response = itemService.updateTradeStatus(request);

    // then
    assertThat(response.getItem().getItemStatus()).isEqualTo(ItemStatus.EXCHANGED);
    verify(itemRepository).save(item);
  }

  @Test
  void 타인의_물품은_거래상태_변경에_실패한다() {
    // given
    Member otherUser = Member.builder().memberId(UUID.randomUUID()).build();
    when(itemRepository.findById(item.getItemId()))
        .thenReturn(Optional.of(item));

    ItemRequest request = new ItemRequest();
    request.setItemId(item.getItemId());
    request.setMember(otherUser);
    request.setItemStatus(ItemStatus.RESERVED);

    // when & then
    assertThatThrownBy(() -> itemService.updateTradeStatus(request))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.INVALID_ITEM_OWNER.getMessage());
  }

  @Test
  void 존재하지_않는_물품이면_예외를_던진다() {
    // given
    UUID invalidId = UUID.randomUUID();
    when(itemRepository.findById(invalidId)).thenReturn(Optional.empty());

    ItemRequest request = new ItemRequest();
    request.setItemId(invalidId);
    request.setMember(owner);
    request.setItemStatus(ItemStatus.RESERVED);

    // when & then
    assertThatThrownBy(() -> itemService.updateTradeStatus(request))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.ITEM_NOT_FOUND.getMessage());
  }
}