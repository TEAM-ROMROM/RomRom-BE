package com.romrom.notification.campaign;

import com.romrom.common.constant.DeviceType;
import com.romrom.common.util.JsonUtil;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.Builder;
import org.springframework.util.StringUtils;

/**
 * 캠페인 대상 조건 (push_campaign.target_filter JSON)
 * SEGMENT 6개: 기기 종류, 마케팅 동의, 최근 활동 N일 이내, N일 이상 미접속, 가입일 범위, 시·도 / MEMBERS: 회원 식별자 / TEST: 관리자 ID
 */
@Builder(toBuilder = true)
public record PushCampaignTargetFilter(
    DeviceType deviceType,
    Boolean isMarketingAgreed,
    Integer activeWithinDays,
    Integer inactiveForDays,
    LocalDate joinedFromDate,
    LocalDate joinedToDate,
    String siDo,
    List<String> memberIdentifiers,
    UUID testMemberId
) {

  // 저장 JSON 키
  private static final String DEVICE_TYPE_KEY = "deviceType";
  private static final String IS_MARKETING_AGREED_KEY = "isMarketingAgreed";
  private static final String ACTIVE_WITHIN_DAYS_KEY = "activeWithinDays";
  private static final String INACTIVE_FOR_DAYS_KEY = "inactiveForDays";
  private static final String JOINED_FROM_DATE_KEY = "joinedFromDate";
  private static final String JOINED_TO_DATE_KEY = "joinedToDate";
  private static final String SI_DO_KEY = "siDo";
  private static final String MEMBER_IDENTIFIERS_KEY = "memberIdentifiers";
  private static final String TEST_MEMBER_ID_KEY = "testMemberId";
  // 회원 식별자 저장 구분자
  private static final String MEMBER_IDENTIFIER_DELIMITER = "\n";

  public static PushCampaignTargetFilter empty() {
    return PushCampaignTargetFilter.builder().build();
  }

  /**
   * 조건 > 저장 JSON (빈 값 제외)
   */
  public String toJson() {
    Map<String, String> storedValueByKey = new HashMap<>();
    putIfPresent(storedValueByKey, DEVICE_TYPE_KEY, deviceType);
    putIfPresent(storedValueByKey, IS_MARKETING_AGREED_KEY, isMarketingAgreed);
    putIfPresent(storedValueByKey, ACTIVE_WITHIN_DAYS_KEY, activeWithinDays);
    putIfPresent(storedValueByKey, INACTIVE_FOR_DAYS_KEY, inactiveForDays);
    putIfPresent(storedValueByKey, JOINED_FROM_DATE_KEY, joinedFromDate);
    putIfPresent(storedValueByKey, JOINED_TO_DATE_KEY, joinedToDate);
    putIfPresent(storedValueByKey, SI_DO_KEY, StringUtils.hasText(siDo) ? siDo : null);
    if (memberIdentifiers != null && !memberIdentifiers.isEmpty()) {
      storedValueByKey.put(MEMBER_IDENTIFIERS_KEY, String.join(MEMBER_IDENTIFIER_DELIMITER, memberIdentifiers));
    }
    putIfPresent(storedValueByKey, TEST_MEMBER_ID_KEY, testMemberId);
    return JsonUtil.convertToJson(storedValueByKey);
  }

  /**
   * 저장 JSON > 조건 (null·빈 값 > 빈 조건)
   */
  public static PushCampaignTargetFilter fromJson(String targetFilterJson) {
    Map<String, String> storedValueByKey = JsonUtil.convertToStringMap(targetFilterJson);
    String memberIdentifiersText = storedValueByKey.get(MEMBER_IDENTIFIERS_KEY);
    return PushCampaignTargetFilter.builder()
        .deviceType(parseIfPresent(storedValueByKey, DEVICE_TYPE_KEY, DeviceType::valueOf))
        .isMarketingAgreed(parseIfPresent(storedValueByKey, IS_MARKETING_AGREED_KEY, Boolean::valueOf))
        .activeWithinDays(parseIfPresent(storedValueByKey, ACTIVE_WITHIN_DAYS_KEY, Integer::valueOf))
        .inactiveForDays(parseIfPresent(storedValueByKey, INACTIVE_FOR_DAYS_KEY, Integer::valueOf))
        .joinedFromDate(parseIfPresent(storedValueByKey, JOINED_FROM_DATE_KEY, LocalDate::parse))
        .joinedToDate(parseIfPresent(storedValueByKey, JOINED_TO_DATE_KEY, LocalDate::parse))
        .siDo(storedValueByKey.get(SI_DO_KEY))
        .memberIdentifiers(memberIdentifiersText == null ? List.of()
            : Arrays.asList(memberIdentifiersText.split(MEMBER_IDENTIFIER_DELIMITER)))
        .testMemberId(parseIfPresent(storedValueByKey, TEST_MEMBER_ID_KEY, UUID::fromString))
        .build();
  }

  private static void putIfPresent(Map<String, String> storedValueByKey, String filterKey, Object filterValue) {
    if (filterValue != null) {
      storedValueByKey.put(filterKey, filterValue.toString());
    }
  }

  private static <T> T parseIfPresent(Map<String, String> storedValueByKey, String filterKey, Function<String, T> parser) {
    String storedValue = storedValueByKey.get(filterKey);
    return StringUtils.hasText(storedValue) ? parser.apply(storedValue) : null;
  }
}
