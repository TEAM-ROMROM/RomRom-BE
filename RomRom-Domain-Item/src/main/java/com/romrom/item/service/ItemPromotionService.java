package com.romrom.item.service;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 롬업(보상형 광고 우선 노출, #819): 활성화 = 검증 > 한도 확인 > 이력 저장 > 물품 만료 일시 갱신
 * 광고 보상은 클라이언트 호출을 신뢰 (AdMob SSV 미도입 > 아래 한도로만 남용 제한, SSV 검증은 후속 보강 과제)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemPromotionService {

  // 우선 노출 유지 시간 (24시간)
  public static final long PROMOTION_DURATION_HOURS = 24;
  // 같은 물품 재활성화 제한 (24시간에 1회)
  public static final long ITEM_PROMOTION_COOLDOWN_HOURS = 24;
  // 회원 하루(KST 달력 기준) 최대 활성화 횟수
  public static final long MEMBER_DAILY_PROMOTION_LIMIT = 3;
  // 회원 하루 한도의 날짜 기준 시간대
  public static final ZoneId PROMOTION_DAY_ZONE = ZoneId.of("Asia/Seoul");

  private final ItemRepository itemRepository;
  private final ItemPromotionHistoryRepository itemPromotionHistoryRepository;

  /**
   * 롬업 활성화: 물품 잠금 조회 > 소유자·상태 검증 > 물품 24시간 한도 > 회원 하루 한도 > 이력 저장 > 만료 일시 갱신
   * 회원 한도는 물품 단위 잠금만 사용 (서로 다른 물품 동시 요청 경합은 허용 오차, 클라이언트 중복 요청 방지와 병행)
   */
  @Transactional
  public ItemResponse activatePromotion(ItemRequest request) {
    Member requestMember = request.getMember();
    LocalDateTime now = LocalDateTime.now();

    Item item = itemRepository.findNotDeletedItemByItemIdForUpdate(request.getItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
    validatePromotableItem(item, requestMember);
    validateItemPromotionCooldown(item, now);
    validateMemberDailyPromotionLimit(requestMember, now);

    LocalDateTime promotionExpiresAt = now.plusHours(PROMOTION_DURATION_HOURS);
    itemPromotionHistoryRepository.save(ItemPromotionHistory.builder()
        .member(requestMember)
        .item(item)
        .promotionStartedAt(now)
        .promotionExpiresAt(promotionExpiresAt)
        .build());
    item.setPromotionExpiresAt(promotionExpiresAt);
    log.debug("[item promotion] activated : itemId={}, expiresAt={}", item.getItemId(), promotionExpiresAt);

    return ItemResponse.builder()
        .item(itemRepository.save(item))
        .build();
  }

  /**
   * KST 오늘 0시 > 서버 시간대 LocalDateTime (DB 저장 기준과 맞춤)
   */
  static LocalDateTime toPromotionDayStart(LocalDateTime serverNow, ZoneId serverZone) {
    LocalDate promotionDay = serverNow.atZone(serverZone).withZoneSameInstant(PROMOTION_DAY_ZONE).toLocalDate();
    return promotionDay.atStartOfDay(PROMOTION_DAY_ZONE).withZoneSameInstant(serverZone).toLocalDateTime();
  }

  //-------------------------------- private 메서드 --------------------------------//

  /**
   * 활성화 가능 물품 검증 (본인 물품, 교환 가능, 관리자 차단 아님)
   */
  private void validatePromotableItem(Item item, Member requestMember) {
    if (!item.getMember().getMemberId().equals(requestMember.getMemberId())) {
      throw new CustomException(ErrorCode.INVALID_ITEM_OWNER);
    }
    boolean isAdminHidden = Boolean.TRUE.equals(item.getIsAdminHidden());
    if (item.getItemStatus() != ItemStatus.AVAILABLE || isAdminHidden) {
      throw new CustomException(ErrorCode.ITEM_NOT_PROMOTABLE);
    }
  }

  private void validateItemPromotionCooldown(Item item, LocalDateTime now) {
    LocalDateTime cooldownStartedAt = now.minusHours(ITEM_PROMOTION_COOLDOWN_HOURS);
    if (itemPromotionHistoryRepository.existsByItemItemIdAndPromotionStartedAtAfter(item.getItemId(), cooldownStartedAt)) {
      log.debug("[item promotion] rejected 409 : item cooldown, itemId={}", item.getItemId());
      throw new CustomException(ErrorCode.ITEM_PROMOTION_ALREADY_ACTIVE);
    }
  }

  private void validateMemberDailyPromotionLimit(Member requestMember, LocalDateTime now) {
    LocalDateTime promotionDayStart = toPromotionDayStart(now, ZoneId.systemDefault());
    long todayPromotionCount = itemPromotionHistoryRepository
        .countByMemberMemberIdAndPromotionStartedAtGreaterThanEqual(requestMember.getMemberId(), promotionDayStart);
    if (todayPromotionCount >= MEMBER_DAILY_PROMOTION_LIMIT) {
      log.debug("[item promotion] rejected 429 : member daily limit, memberId={}", requestMember.getMemberId());
      throw new CustomException(ErrorCode.ITEM_PROMOTION_DAILY_LIMIT_EXCEEDED);
    }
  }
}
