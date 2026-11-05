package com.romrom.application.util;

import com.romrom.application.dto.AdminPushCampaignRequest;
import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.common.constant.PushCampaignTargetType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.notification.campaign.AdvertisementPushRule;
import com.romrom.notification.campaign.PushCampaignPayloadFactory;
import com.romrom.notification.campaign.PushCampaignTargetFilter;
import com.romrom.notification.entity.PushCampaign;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 관리자 푸시 입력 검증·변환 (#874): 제목·본문 길이, 이미지(https), 딥링크(romrom·https, 공지·물품은 UUID),
 * 대상 조건(일수 범위, 식별자 최대 1000개), 예약 시각(미래), 광고성 야간 금지
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PushCampaignInputRule {

  // 최근 활동·미접속 일수 범위 (1일 ~ 10년)
  private static final int MINIMUM_DAYS = 1;
  private static final int MAXIMUM_DAYS = 3650;
  // MEMBERS 식별자 최대 개수
  public static final int MAXIMUM_MEMBER_IDENTIFIER_COUNT = 1000;
  // 식별자 구분자 (줄바꿈·쉼표·세미콜론·탭)
  private static final String MEMBER_IDENTIFIER_SPLIT_REGEX = "[\\r\\n,;\\t]+";
  private static final String HTTPS_PREFIX = "https://";

  /**
   * 제목·본문 검증 > 앞뒤 공백 제거 값 (빈 값·길이 초과 > INVALID_PUSH_CAMPAIGN_CONTENT)
   */
  public static String toValidatedText(String text, int maximumLength) {
    if (!StringUtils.hasText(text) || text.trim().length() > maximumLength) {
      log.warn("[push campaign] rejected 400 : title or body empty or too long");
      throw new CustomException(ErrorCode.INVALID_PUSH_CAMPAIGN_CONTENT);
    }
    return text.trim();
  }

  /**
   * 이미지 URL: 빈 값 > null, https:// 아님·길이 초과 > INVALID_PUSH_IMAGE_URL
   */
  public static String toValidatedImageUrl(String imageUrl) {
    if (!StringUtils.hasText(imageUrl)) {
      return null;
    }
    String trimmedImageUrl = imageUrl.trim();
    if (!trimmedImageUrl.toLowerCase(Locale.ROOT).startsWith(HTTPS_PREFIX)
        || trimmedImageUrl.length() > PushCampaign.LINK_MAXIMUM_LENGTH) {
      log.warn("[push campaign] rejected 400 : image url is not https");
      throw new CustomException(ErrorCode.INVALID_PUSH_IMAGE_URL);
    }
    return trimmedImageUrl;
  }

  /**
   * 딥링크: 빈 값 > null, romrom://·https:// 아님·길이 초과·공지·물품 ID 형식 오류 > INVALID_PUSH_DEEP_LINK
   */
  public static String toValidatedDeepLink(String deepLink) {
    if (!StringUtils.hasText(deepLink)) {
      return null;
    }
    String trimmedDeepLink = deepLink.trim();
    boolean isValidDeepLink = AdminLinkUrlRule.isAllowedLinkUrl(trimmedDeepLink)
        && trimmedDeepLink.length() <= PushCampaign.LINK_MAXIMUM_LENGTH
        && hasValidIdAfterPrefix(trimmedDeepLink, PushCampaignPayloadFactory.ANNOUNCEMENT_DETAIL_DEEP_LINK_PREFIX)
        && hasValidIdAfterPrefix(trimmedDeepLink, PushCampaignPayloadFactory.ITEM_DETAIL_DEEP_LINK_PREFIX);
    if (!isValidDeepLink) {
      log.warn("[push campaign] rejected 400 : invalid deep link");
      throw new CustomException(ErrorCode.INVALID_PUSH_DEEP_LINK);
    }
    return trimmedDeepLink;
  }

  /**
   * 대상 조건 변환: SEGMENT 6개, MEMBERS 식별자, TEST 관리자 ID (그 외 조건 무시)
   */
  public static PushCampaignTargetFilter toTargetFilter(PushCampaignTargetType targetType,
      AdminPushCampaignRequest request) {
    return switch (targetType) {
      case ALL -> PushCampaignTargetFilter.empty();
      case TEST -> PushCampaignTargetFilter.builder().testMemberId(request.getExecutorAdminId()).build();
      case MEMBERS -> PushCampaignTargetFilter.builder()
          .memberIdentifiers(toMemberIdentifiers(request.getMemberIdentifiers()))
          .build();
      case SEGMENT -> toSegmentFilter(request);
    };
  }

  /**
   * 식별자 문자열 > 목록 (빈 값 제거·중복 제거, 0개·1000개 초과 > INVALID_PUSH_CAMPAIGN_TARGET)
   */
  public static List<String> toMemberIdentifiers(String memberIdentifiersText) {
    List<String> memberIdentifiers = memberIdentifiersText == null ? List.of()
        : Arrays.stream(memberIdentifiersText.split(MEMBER_IDENTIFIER_SPLIT_REGEX))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    if (memberIdentifiers.isEmpty() || memberIdentifiers.size() > MAXIMUM_MEMBER_IDENTIFIER_COUNT) {
      log.warn("[push campaign] rejected 400 : member identifiers count={}", memberIdentifiers.size());
      throw new CustomException(ErrorCode.INVALID_PUSH_CAMPAIGN_TARGET);
    }
    return memberIdentifiers;
  }

  /**
   * 예약 시각: 미입력 > null(즉시), 현재 이전 > INVALID_PUSH_CAMPAIGN_SCHEDULE
   */
  public static LocalDateTime toValidatedScheduledAt(String scheduledAtText, LocalDateTime now) {
    LocalDateTime scheduledAt = AdminDateRangeParser.parseLocalDateTime(scheduledAtText);
    if (scheduledAt != null && !scheduledAt.isAfter(now)) {
      log.warn("[push campaign] rejected 400 : scheduledAt is not in the future");
      throw new CustomException(ErrorCode.INVALID_PUSH_CAMPAIGN_SCHEDULE);
    }
    return scheduledAt;
  }

  /**
   * 광고성 야간(21:00~08:00 KST) 발송·예약 거부
   */
  public static void validateAdvertisementSendTime(PushCampaignPurpose purpose, LocalDateTime kstSendAt) {
    if (AdvertisementPushRule.isRestrictedAdvertisementAt(purpose, kstSendAt)) {
      log.warn("[push campaign] rejected 400 : advertisement night restriction, sendAt={}", kstSendAt);
      throw new CustomException(ErrorCode.ADVERTISEMENT_PUSH_NIGHT_RESTRICTED);
    }
  }

  // SEGMENT 조건 6개 (일수 범위 1~3650, 가입일 yyyy-MM-dd·시작 <= 종료)
  private static PushCampaignTargetFilter toSegmentFilter(AdminPushCampaignRequest request) {
    LocalDate joinedFromDate = parseDateOrNull(request.getJoinedFromDate());
    LocalDate joinedToDate = parseDateOrNull(request.getJoinedToDate());
    boolean isJoinedRangeReversed = joinedFromDate != null && joinedToDate != null && joinedToDate.isBefore(joinedFromDate);
    if (isJoinedRangeReversed || !isDaysInRange(request.getActiveWithinDays()) || !isDaysInRange(request.getInactiveForDays())) {
      log.warn("[push campaign] rejected 400 : invalid segment filter");
      throw new CustomException(ErrorCode.INVALID_PUSH_CAMPAIGN_TARGET);
    }
    return PushCampaignTargetFilter.builder()
        .deviceType(request.getDeviceType())
        .isMarketingAgreed(request.getIsMarketingAgreed())
        .activeWithinDays(request.getActiveWithinDays())
        .inactiveForDays(request.getInactiveForDays())
        .joinedFromDate(joinedFromDate)
        .joinedToDate(joinedToDate)
        .siDo(StringUtils.hasText(request.getSiDo()) ? request.getSiDo().trim() : null)
        .build();
  }

  private static boolean isDaysInRange(Integer days) {
    return days == null || (days >= MINIMUM_DAYS && days <= MAXIMUM_DAYS);
  }

  private static LocalDate parseDateOrNull(String dateText) {
    LocalDateTime startOfDay = AdminDateRangeParser.parseStartOfDay(dateText);
    return startOfDay == null ? null : startOfDay.toLocalDate();
  }

  // 접두 일치 시 뒤가 UUID 인지 (접두 불일치 > 통과)
  private static boolean hasValidIdAfterPrefix(String deepLink, String deepLinkPrefix) {
    if (!deepLink.startsWith(deepLinkPrefix)) {
      return true;
    }
    try {
      UUID.fromString(deepLink.substring(deepLinkPrefix.length()));
      return true;
    } catch (IllegalArgumentException invalidIdException) {
      return false;
    }
  }
}
