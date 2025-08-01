package com.romrom.item.service;

import com.romrom.ai.service.EmbeddingService;
import com.romrom.ai.service.VertexAiClient;
import com.romrom.common.constant.LikeContentType;
import com.romrom.common.constant.LikeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.dto.ItemDetail;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import com.romrom.item.entity.mongo.LikeHistory;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.repository.mongo.LikeHistoryRepository;
import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemService {

  private final ItemRepository itemRepository;
  private final ItemCustomTagsService itemCustomTagsService;
  private final ItemImageService itemImageService;
  private final LikeHistoryRepository likeHistoryRepository;
  private final EmbeddingService embeddingService;
  private final VertexAiClient vertexAiClient;
  private final ItemImageRepository itemImageRepository;
  private final MemberRepository memberRepository;
  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;

  // 물품 등록
  @Transactional
  public ItemResponse postItem(ItemRequest request) {

    Member member = request.getMember();

    // Item 엔티티 생성 및 저장
    Item item = Item.builder()
        .member(member)
        .itemName(request.getItemName())
        .itemDescription(request.getItemDescription())
        .itemCategory(request.getItemCategory())
        .itemCondition(request.getItemCondition())
        .itemTradeOptions(request.getItemTradeOptions())
        .location(convertToPoint(request.getLongitude(), request.getLatitude()))
        .price(request.getItemPrice())
        .likeCount(0)
        .build();
    itemRepository.save(item);

    // 커스텀 태그 서비스 코드 추가
    List<String> customTags = itemCustomTagsService.updateTags(item.getItemId(), request.getItemCustomTags());

    // 이미지 업로드 및 ItemImage 엔티티 저장
    List<ItemImage> itemImages = itemImageService.saveItemImages(item, request.getItemImages());

    // 첫 물품 등록 여부가 false 일 경우 true 로 업데이트
    if (!member.getIsFirstItemPosted()) {
      member.setIsFirstItemPosted(true);
      // CustomUserDetails의 member는 비영속 상태이기 떄문에, save 메서드 필요
      memberRepository.save(member);
    }

    // 아이템 임베딩 값 저장
    embeddingService.generateAndSaveItemEmbedding(extractItemText(item), item.getItemId());

    return ItemResponse.builder()
        .item(item)
        .itemImages(itemImages)
        .itemCustomTags(customTags)
        .build();
  }

  // 물품 수정
  @Transactional
  public ItemResponse updateItem(ItemRequest request) {
    // 1) 기존 아이템 조회 및 권한 체크
    Item item = findAndAuthorize(request);

    // 2) 필드 업데이트
    applyRequestToItem(request, item);
    itemRepository.save(item);

    // 3) 임베딩 삭제 및 재생성
    embeddingService.deleteItemEmbedding(item.getItemId());
    embeddingService.generateAndSaveItemEmbedding(extractItemText(item), item.getItemId());

    // 4) 이미지 업데이트
    // todo: 프론트측 아이템 이미지 업데이트 요청시, 아래 로직(삭제 후 저장)으로 수행 가능한지 생각
    itemImageService.deleteItemImages(item);
    List<ItemImage> itemImages = itemImageService.saveItemImages(item, request.getItemImages());

    // 5) 태그 업데이트 - 몽고디비는 Replica Set 및 세팅 안할시 Transactional 적용 안돼서 순서 맨 끝으로 뺌
    List<String> customTags = itemCustomTagsService.updateTags(item.getItemId(), request.getItemCustomTags());

    // 6) 응답 빌드
    return ItemResponse.builder()
        .item(item)
        .itemImages(itemImages)
        .itemCustomTags(customTags)
        .build();
  }

  // 물품 삭제
  @Transactional
  public void deleteItem(ItemRequest request) {
    // 1) 기존 아이템 조회 및 권한 체크
    Item item = findAndAuthorize(request);

    // 2) 관련 리소스 삭제 (이미지, 태그, 임베딩 등) 후 아이템 삭제
    deleteRelatedItemInfo(item);
    itemRepository.deleteByItemId(item.getItemId());
  }

  /**
   * 물품 목록 조회
   *
   * @param request 필터링 및 페이징 요청 정보
   * @return 페이지네이션된 물품 응답
   */
  @Transactional(readOnly = true)
  public ItemResponse getItemsSortsByCreatedDate(ItemRequest request) {
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize()
    );

    // 최신순으로 정렬된 Item 페이지 조회
    Page<Item> itemPage = itemRepository.findAllByOrderByCreatedDateDesc(pageable);

    // ItemPage > ItemDetailPage 변환 후 DTO에 입력
    return ItemResponse.builder()
        .itemDetailPage(getItemDetailPageFromItemPage(itemPage))
        .build();
  }

  /**
   * 내가 등록한 물품 조회
   */
  @Transactional(readOnly = true)
  public ItemResponse getMyItems(ItemRequest request) {
    Pageable pageable = PageRequest.of(request.getPageNumber(), request.getPageSize());
    Page<Item> itemPage = itemRepository.findAllByMember(request.getMember(), pageable);
    return ItemResponse.builder()
        .itemDetailPage(getItemDetailPageFromItemPage(itemPage))
        .build();
  }

  @Transactional(readOnly = true)
  public List<Item> getMyItemIds(Member member) {
    return itemRepository.findAllByMember(member);
  }
  
  /**
   * 물품 상세 조회
   *
   * @param request 물품 상세 조회 요청 정보
   * @return 물품 상세 조회
   */
  @Transactional(readOnly = true)
  public ItemResponse getItemDetail(ItemRequest request) {
    // 아이템 조회
    Item item = itemRepository.findById(request.getItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    // 아이템 이미지 조회
    List<ItemImage> itemImages = itemImageRepository.findAllByItem(item);

    // 커스텀 태그 조회
    List<String> customTags = itemCustomTagsService.getTags(item.getItemId());

    // 좋아요 상태 조회
    LikeStatus likeStatus = getLikeStatus(item, request.getMember());

    return ItemResponse.builder()
        .item(item)
        .itemImages(itemImages)
        .itemCustomTags(customTags)
        .likeStatus(likeStatus)
        .likeCount(item.getLikeCount())
        .build();
  }

  // 좋아요 등록 및 취소
  @Transactional
  public ItemResponse likeOrUnlikeItem(ItemRequest request) {
    Member member = request.getMember();
    Item item = itemRepository.findById(request.getItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    // 본인 게시물에는 좋아요 달 수 없으므로 예외 처리
    if (member.getMemberId().equals(item.getMember().getMemberId())) {
      log.debug("좋아요 등록 실패 : 본인의 게시물에는 좋아요를 달 수 없음");
      throw new CustomException(ErrorCode.SELF_LIKE_NOT_ALLOWED);
    }

    // 좋아요 존재시 취소 로직
    if (likeHistoryRepository.existsByMemberIdAndItemId(member.getMemberId(), item.getItemId())) {
      log.debug("이미 좋아요를 누른 글에는 좋아요 취소를 진행합니다 : 물품={}", item.getItemId());

      likeHistoryRepository.deleteByMemberIdAndItemId(member.getMemberId(), item.getItemId());
      item.decreaseLikeCount();
      Item savedDecresedLikeItem = itemRepository.save(item);
      log.debug("좋아요 취소 완료 : likes={}", item.getLikeCount());

      return ItemResponse.builder()
          .item(savedDecresedLikeItem)
          .likeStatus(LikeStatus.UNLIKE)
          .likeCount(item.getLikeCount())
          .build();
    }

    log.debug("좋아요가 없는 글에는 좋아요 등록을 진행합니다 : 물품={}", item.getItemId());
    likeHistoryRepository.save(LikeHistory.builder()
        .itemId(item.getItemId())
        .memberId(member.getMemberId())
        .likeContentType(LikeContentType.ITEM)
        .build());

    item.increaseLikeCount();
    Item savedIncreasedLikeItem = itemRepository.save(item);
    log.debug("좋아요 등록 완료 : likes={}", item.getLikeCount());
    return ItemResponse.builder()
        .item(savedIncreasedLikeItem)
        .likeStatus(LikeStatus.LIKE)
        .likeCount(item.getLikeCount())
        .build();
  }

  @Transactional
  public void deleteAllRelatedItemInfoByMemberId(UUID memberId) {
    List<Item> items = itemRepository.findByMemberMemberId(memberId);
    items.forEach(this::deleteRelatedItemInfo);
    itemRepository.deleteByMemberMemberId(memberId);
  }

  /**
   * 제품 설명을 기반으로 중고 거래 가격 예측
   *
   * @param itemRequest 제품 설명 요청 객체
   * @return 예측된 가격 (KRW, 정수)
   */
  public int predictItemPrice(ItemRequest itemRequest) {
    try {
      // 필요한 정보만 추출해서 텍스트 생성
      String itemName = itemRequest.getItemName();
      String description = itemRequest.getItemDescription();
      String condition = itemRequest.getItemCondition() != null ? itemRequest.getItemCondition().name() : "";

      // Vertex AI에 보낼 문장 조합
      StringBuilder promptBuilder = new StringBuilder();
      if (itemName != null)
        promptBuilder.append(itemName).append(", ");
      if (description != null)
        promptBuilder.append(description).append(", ");
      if (!condition.isEmpty())
        promptBuilder.append("상태: ").append(condition);

      String prompt = promptBuilder.toString();

      log.debug("중고가 예측 요청 문장: {}", prompt);
      return vertexAiClient.getItemPricePrediction(prompt);

    } catch (Exception e) {
      log.error("가격 예측 실패: {}", itemRequest, e);
      throw new CustomException(ErrorCode.ITEM_VALUE_PREDICTION_FAILED);
    }
  }

  @Transactional
  public ItemResponse updateTradeStatus(ItemRequest request) {
    Item item = findAndAuthorize(request);

    item.setItemStatus(request.getItemStatus());
    return ItemResponse.builder()
        .item(itemRepository.save(item))
        .itemImages(itemImageRepository.findAllByItem(item))
        .itemCustomTags(itemCustomTagsService.getTags(item.getItemId()))
        .likeStatus(getLikeStatus(item, request.getMember()))
        .likeCount(item.getLikeCount())
        .build();
  }

  //-------------------------------- private 메서드 --------------------------------//

  /**
   * 위도·경도로부터 JTS Point 객체를 만들어 반환
   */
  private Point convertToPoint(Double longitude, Double latitude) {
    GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
    return gf.createPoint(new Coordinate(longitude, latitude));
  }

  private Page<ItemDetail> getItemDetailPageFromItemPage(Page<Item> itemPage) {
    return itemPage.map(item -> ItemDetail.from(item, itemImageRepository.findAllByItem(item), itemCustomTagsService.getTags(item.getItemId())));
  }

  /**
   * Item 도메인 관련 데이터 삭제
   */
  private void deleteRelatedItemInfo(Item item) {
    tradeRequestHistoryRepository.deleteAllByGiveItemItemId(item.getItemId());
    tradeRequestHistoryRepository.deleteAllByTakeItemItemId(item.getItemId());
    itemImageService.deleteItemImages(item);
    itemCustomTagsService.deleteAllTags(item.getItemId());
    embeddingService.deleteItemEmbedding(item.getItemId());
  }

  /**
   * 아이템 조회 및 권한 체크
   */
  private Item findAndAuthorize(ItemRequest request) {
    Member member = request.getMember();
    UUID itemId = request.getItemId();
    Item item = itemRepository.findById(itemId)
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
    if (!item.getMember().getMemberId().equals(member.getMemberId())) {
      throw new CustomException(ErrorCode.INVALID_ITEM_OWNER);
    }
    return item;
  }

  /**
   * ItemRequest 값을 Item 엔티티에 적용
   */
  private void applyRequestToItem(ItemRequest request, Item item) {
    item.setItemName(request.getItemName());
    item.setItemDescription(request.getItemDescription());
    item.setItemCategory(request.getItemCategory());
    item.setItemCondition(request.getItemCondition());
    item.setItemTradeOptions(request.getItemTradeOptions());
    item.setLocation(convertToPoint(request.getLongitude(), request.getLatitude()));
    item.setPrice(request.getItemPrice());
  }

  private LikeStatus getLikeStatus(Item item, Member member) {
    boolean liked = likeHistoryRepository.existsByMemberIdAndItemId(member.getMemberId(), item.getItemId());
    return liked ? LikeStatus.LIKE : LikeStatus.UNLIKE;
  }

  private String extractItemText(Item item) {
    return item.getItemName() + ", " + item.getItemDescription();
  }
}
