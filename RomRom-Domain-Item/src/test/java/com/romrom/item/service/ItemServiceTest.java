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
import com.romrom.member.service.MemberLocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

  @Mock private ItemRepository itemRepository;
  @Mock private ItemCustomTagsService itemCustomTagsService;
  @Mock private MemberLocationService memberLocationService;
  @Mock private LikeHistoryRepository likeHistoryRepository;
  @Mock private EmbeddingService embeddingService;
  @Mock private VertexAiClient vertexAiClient;
  @Mock private ItemImageRepository itemImageRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private TradeRequestHistoryRepository tradeRequestHistoryRepository;
  @Mock private ItemDetailAssembler itemDetailAssembler;

  private ItemService itemService;
  private Member owner;
  private Item item;

  @BeforeEach
  void setUp() {
    itemService = new ItemService(
        itemRepository,
        likeHistoryRepository,
        itemCustomTagsService,
        memberLocationService,
        embeddingService,
        vertexAiClient,
        memberRepository,
        itemImageRepository,
        tradeRequestHistoryRepository,
        itemDetailAssembler
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
    ItemResponse response = itemService.updateItemStatus(request);

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
    request.setItemStatus(ItemStatus.EXCHANGED);

    // when & then
    assertThatThrownBy(() -> itemService.updateItemStatus(request))
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
    request.setItemStatus(ItemStatus.EXCHANGED);

    // when & then
    assertThatThrownBy(() -> itemService.updateItemStatus(request))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(ErrorCode.ITEM_NOT_FOUND.getMessage());
  }
}