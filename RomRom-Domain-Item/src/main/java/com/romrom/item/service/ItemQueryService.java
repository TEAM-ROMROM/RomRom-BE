package com.romrom.item.service;

import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemSortField;
import com.romrom.common.constant.OriginalType;
import com.romrom.common.entity.postgres.Embedding;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.repository.EmbeddingRepository;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import com.romrom.item.entity.mongo.LikeHistory;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.UserInteractionScore;
import com.romrom.item.repository.mongo.LikeHistoryRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.UserInteractionScoreRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberItemCategory;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberItemCategoryRepository;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.member.repository.MemberRepository;
import com.romrom.member.service.MemberBlockService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geolatte.geom.G2D;
import org.geolatte.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 물품 조회: 목록, 내 물품, 회원 물품, 상세, 공개 상세, 좋아요 목록
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemQueryService {

  // 단건 조회 (findItemById) 단일 원본
  private final ItemService itemService;
  private final ItemRepository itemRepository;
  private final LikeHistoryRepository likeHistoryRepository;
  private final UserInteractionService userInteractionService;
  private final MemberRepository memberRepository;
  private final MemberLocationRepository memberLocationRepository;
  private final EmbeddingRepository embeddingRepository;
  private final MemberItemCategoryRepository memberItemCategoryRepository;
  private final MemberBlockService memberBlockService;
  private final UserInteractionScoreRepository userInteractionScoreRepository;

  /**
   * 물품 목록 조회
   *
   * @param request 필터링 및 페이징 요청 정보
   * @return 페이지네이션된 물품 응답
   */
  @Transactional(readOnly = true)
  public ItemResponse getItemList(ItemRequest request) {

    // 정렬 기준 및 방향 설정
    ItemSortField sortField = request.getSortField();
    Sort.Direction dir = request.getSortDirection();

    // 선호 임베딩 조회
    float[] memberEmbedding = null;
    if (sortField == ItemSortField.PREFERRED_CATEGORY) {
      log.debug("회원 임베딩 조회: memberId={}", request.getMember().getMemberId());
      memberEmbedding = embeddingRepository
          .findFirstByOriginalIdAndOriginalTypeOrderByCreatedDateDesc(request.getMember().getMemberId(), OriginalType.CATEGORY)
          .map(Embedding::getEmbedding)
          .orElseThrow(() -> new CustomException(ErrorCode.EMBEDDING_NOT_FOUND));
    }

    // 회원 위치 조회 (위치 미등록 시 최신순으로 폴백)
    Double longitude = null;
    Double latitude = null;
    if (sortField == ItemSortField.DISTANCE || sortField == ItemSortField.RECOMMENDED) {
      Optional<MemberLocation> memberLocationOptional = memberLocationRepository.findByMemberMemberId(request.getMember().getMemberId());
      if (memberLocationOptional.isPresent()) {
        Point<G2D> geom = memberLocationOptional.get().getGeom();
        longitude = geom.getPosition().getLon();
        latitude = geom.getPosition().getLat();
      } else {
        // 위치 미등록 회원은 최신순으로 폴백
        log.warn("위치 미등록 회원 - 최신순으로 폴백: memberId={}", request.getMember().getMemberId());
        sortField = ItemSortField.CREATED_DATE;
      }
    }

    // Pageable은 위치 폴백 이후 확정된 sortField로 생성
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(dir, sortField.getProperty())
    );

    // 회원 탐색 범위 조회
    Double radiusInMeters = request.getMember().getSearchRadiusInMeters();

    if (radiusInMeters == null) {
      radiusInMeters = request.getRadiusInMeters();
    }

    // 회원 탐색 범위 미설정 시 10km로 적용
    if (radiusInMeters == null) {
      radiusInMeters = 10000.0;
      log.debug("회원 탐색 범위 미설정 - 10km 적용: memberId={}", request.getMember().getMemberId());
    }

    List<UserInteractionScore> userScores = null;
    List<ItemCategory> preferredCategories = null;

    // 사용자 상호작용 점수 및 선호 카테고리 조회
    if (sortField == ItemSortField.RECOMMENDED) {
      userScores = userInteractionScoreRepository.findByMemberMemberId(request.getMember().getMemberId());
      preferredCategories = memberItemCategoryRepository.findByMemberMemberId(request.getMember().getMemberId())
          .stream()
          .map(MemberItemCategory::getItemCategory)
          .toList();
    }

    // 필터링된 아이템 목록 조회
    Page<Item> itemPage = itemRepository.filterItems(
        request.getMember().getMemberId(),
        longitude,
        latitude,
        radiusInMeters,
        memberEmbedding,
        userScores,
        preferredCategories,
        sortField,
        pageable
    );

    return ItemResponse.builder()
        .itemPage(itemPage)
        .build();
  }

  /**
   * 내가 등록한 물품 조회
   *
   * @param request 물품 조회 요청 정보
   * @return 내가 등록한 물품 목록
   */
  @Transactional(readOnly = true)
  public ItemResponse getMyItemsFetchJoinMemberDesc(ItemRequest request) {
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Direction.DESC, "createdDate") // Spring Data JPA의 정렬은 엔티티 필드명(camelCase) 기준
    );

    Page<Item> itemPage;
    if (request.getItemStatus() == null) {
      itemPage = itemRepository.findAllByMemberAndIsDeletedFalse(request.getMember(), pageable);
    } else {
      itemPage = itemRepository.findAllByMemberAndItemStatusAndIsDeletedFalseWithMember(request.getMember(), request.getItemStatus(), pageable);
    }
    return ItemResponse.builder()
        .itemPage(itemPage)
        .build();
  }

  // memberId 기반 회원 등록 물품 페이지 조회
  @Transactional(readOnly = true)
  public ItemResponse getMemberItemsByMemberId(ItemRequest request) {
    if (request.getMemberId() == null) {
      log.error("물품 조회 대상 memberId가 누락되었습니다.");
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    Member targetMember = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    // 탈퇴한 회원의 물품 조회 차단
    if (targetMember.getAccountStatus() == AccountStatus.DELETE_ACCOUNT) {
      log.debug("탈퇴한 회원의 물품 조회 시도 차단: memberId={}", targetMember.getMemberId());
      throw new CustomException(ErrorCode.DELETED_MEMBER);
    }

    // 정지된 회원의 물품 조회 차단
    if (targetMember.getAccountStatus() == AccountStatus.SUSPENDED_ACCOUNT) {
      log.debug("정지된 회원의 물품 조회 시도 차단: memberId={}", targetMember.getMemberId());
      throw new CustomException(ErrorCode.SUSPENDED_MEMBER);
    }

    // 요청 회원과 대상 회원 간 차단 관계 검증
    UUID targetMemberId = request.getMemberId();
    memberBlockService.verifyNotBlocked(request.getMember().getMemberId(), targetMemberId);

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Direction.DESC, "createdDate")
    );

    Page<Item> itemPage = itemRepository.findAllByMemberMemberIdAndIsDeletedFalseAndIsAdminHiddenFalse(
        request.getMemberId(), pageable);

    return ItemResponse.builder()
        .itemPage(itemPage)
        .build();
  }

  /**
   * 물품 상세 조회
   *
   * @param request UUID itemId
   * @return 물품 상세 조회
   */
  @Transactional
  public ItemResponse getItemDetail(ItemRequest request) {
    // 아이템 조회
    Item item = itemService.findItemById(request.getItemId());

    // 본인 물품이 아니면 차단 여부 확인 (예외 대신 boolean 플래그 반환)
    boolean isBlocked = false;
    if (!request.getMember().getMemberId().equals(item.getMember().getMemberId())) {
      isBlocked = memberBlockService.isBlocked(request.getMember().getMemberId(), item.getMember().getMemberId());
    }

    // 탈퇴한 사용자 물품 조회 차단
    Member itemOwner = item.getMember();
    if (itemOwner.getAccountStatus() == AccountStatus.DELETE_ACCOUNT) {
      log.debug("탈퇴한 사용자의 물품 조회 시도 차단: memberId={}", itemOwner.getMemberId());
      throw new CustomException(ErrorCode.DELETED_MEMBER);
    }

    // 정지된 사용자 물품 조회 차단
    if (itemOwner.getAccountStatus() == AccountStatus.SUSPENDED_ACCOUNT) {
      log.debug("정지된 사용자의 물품 조회 시도 차단: memberId={}", itemOwner.getMemberId());
      throw new CustomException(ErrorCode.SUSPENDED_MEMBER);
    }

    // 삭제된 물품 조회 차단
    if (item.getIsDeleted()) {
      log.debug("삭제된 물품 조회 시도 차단: itemId={}", item.getItemId());
      throw new CustomException(ErrorCode.DELETED_ITEM);
    }

    // 관리자 노출 차단 물품 조회 차단
    if (item.getIsAdminHidden()) {
      log.debug("관리자 노출 차단 물품 조회 시도 차단: itemId={}", item.getItemId());
      throw new CustomException(ErrorCode.ITEM_ADMIN_HIDDEN);
    }

    // 회원 위치 정보 조회 (위치 미등록 시 null 허용)
    Optional<MemberLocation> itemOwnerLocationOptional = memberLocationRepository.findByMemberMemberId(itemOwner.getMemberId());
    if (itemOwnerLocationOptional.isPresent()) {
      MemberLocation itemOwnerLocation = itemOwnerLocationOptional.get();
      itemOwner.setLatitude(itemOwnerLocation.getLatitude());
      itemOwner.setLongitude(itemOwnerLocation.getLongitude());
    } else {
      log.warn("위치 미등록 회원의 물품 상세 조회: memberId={}", itemOwner.getMemberId());
    }
    item.setMember(itemOwner);

    // 물품 조회 기록 (본인이 등록한 물품은 기록 X)
    if (!request.getMember().getMemberId().equals(item.getMember().getMemberId())) {
      userInteractionService.recordView(
          request.getMember().getMemberId(),
          item.getItemId(),
          item.getItemCategory()
      );
    }

    // 차단 여부를 item에 직접 세팅
    item.setIsBlocked(isBlocked);

    return ItemResponse.builder()
        .item(item)
        .isLiked(isLikedByMember(item, request.getMember()))
        .build();
  }

  /**
   * 공개 물품 상세 조회 (인증 불필요)
   * - 카카오톡 공유 링크 OG 태그 렌더링을 위한 Cloud Function 호출용
   * - 좋아요/차단/신고/조회 기록 로직 없음
   * - 탈퇴/정지 사용자의 물품, 삭제된 물품은 접근 차단
   */
  @Transactional(readOnly = true)
  public ItemResponse getPublicItemDetail(UUID itemId) {
    Item item = itemService.findItemById(itemId);
    Member itemOwner = item.getMember();

    // 탈퇴한 사용자 물품 조회 차단
    if (itemOwner.getAccountStatus() == AccountStatus.DELETE_ACCOUNT) {
      log.debug("탈퇴한 사용자의 공개 물품 조회 시도 차단: memberId={}", itemOwner.getMemberId());
      throw new CustomException(ErrorCode.DELETED_MEMBER);
    }

    // 정지된 사용자 물품 조회 차단
    if (itemOwner.getAccountStatus() == AccountStatus.SUSPENDED_ACCOUNT) {
      log.debug("정지된 사용자의 공개 물품 조회 시도 차단: memberId={}", itemOwner.getMemberId());
      throw new CustomException(ErrorCode.SUSPENDED_MEMBER);
    }

    // 삭제된 물품 조회 차단
    if (item.getIsDeleted()) {
      log.debug("삭제된 물품 공개 조회 시도 차단: itemId={}", item.getItemId());
      throw new CustomException(ErrorCode.DELETED_ITEM);
    }

    // 관리자 노출 차단 물품 공개 조회 차단
    if (item.getIsAdminHidden()) {
      log.debug("관리자 노출 차단 물품 공개 조회 시도 차단: itemId={}", item.getItemId());
      throw new CustomException(ErrorCode.ITEM_ADMIN_HIDDEN);
    }

    return ItemResponse.builder()
        .item(item)
        .build();
  }

  /**
   * 좋아요한 물품 목록 조회
   *
   * @param request UUID memberId
   * @return 좋아요한 물품 목록
   */
  @Transactional(readOnly = true)
  public ItemResponse getLikedItems(ItemRequest request) {
    UUID memberId = request.getMember().getMemberId();

    // 페이징 설정
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "createdDate")
    );

    // LikeHistory 조회 및 정렬
    Page<LikeHistory> likeHistoryPage = likeHistoryRepository.findByMemberId(memberId, pageable);
    log.debug("좋아요 목록 조회 완료: memberId={}, totalLikes={}",
        memberId, likeHistoryPage.getTotalElements());

    // 아이템 ID 목록 추출
    List<UUID> sortedItemIds = likeHistoryPage.getContent().stream()
        .map(LikeHistory::getItemId)
        .collect(Collectors.toList());

    // 해당 아이템 일괄 조회
    List<Item> items = itemRepository.findByItemIdInAndIsDeletedFalse(sortedItemIds, memberId);

    // itemId -> Item 매핑 생성
    Map<UUID, Item> itemMap = items.stream()
        .collect(Collectors.toMap(Item::getItemId, item -> item));

    // 좋아요 목록 순서 기준으로 아이템 재정렬
    List<Item> sortedItems = sortedItemIds.stream()
        .map(itemMap::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    Page<Item> itemPage = new PageImpl<>(sortedItems, pageable, likeHistoryPage.getTotalElements());

    return ItemResponse.builder()
        .itemPage(itemPage)
        .build();
  }

  private boolean isLikedByMember(Item item, Member member) {
    return likeHistoryRepository.existsByMemberIdAndItemId(member.getMemberId(), item.getItemId());
  }
}
