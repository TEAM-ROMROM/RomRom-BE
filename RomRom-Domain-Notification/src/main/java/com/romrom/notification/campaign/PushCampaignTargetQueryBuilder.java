package com.romrom.notification.campaign;

import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.common.constant.PushCampaignTargetType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 캠페인 대상 > SQL 조건 (값은 전부 바인딩 파라미터, 회원 전체를 메모리에 올리지 않고 DB에서 거름)
 * 대상 4개: TEST 관리자 1명 / MEMBERS 식별자(탈퇴 제외) / ALL·SEGMENT 활성 회원(탈퇴·정지·관리자 제외) + 조건
 * 광고성 > 마케팅 수신 동의자만 대상, 수신 동의: 정보성 콘텐츠 알림·광고성 마케팅·TEST 무조건
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PushCampaignTargetQueryBuilder {

  private static final String NOT_DELETED_CLAUSE = "m.is_deleted = false";
  // 활성 회원: 탈퇴·정지 상태와 관리자 계정 제외
  private static final String ACTIVE_MEMBER_CLAUSE = NOT_DELETED_CLAUSE
      + " AND (m.account_status IS NULL OR m.account_status NOT IN ('DELETE_ACCOUNT', 'SUSPENDED_ACCOUNT'))"
      + " AND (m.role IS NULL OR m.role <> 'ROLE_ADMIN')";
  private static final String MARKETING_AGREED_CLAUSE = "m.is_marketing_info_agreed = true";
  private static final String CONTENT_NOTIFICATION_AGREED_CLAUSE = "m.is_content_notification_agreed = true";
  private static final String ALWAYS_TRUE_CLAUSE = "TRUE";
  private static final String ALWAYS_FALSE_CLAUSE = "FALSE";
  // LIKE 접두 검색 이스케이프 문자
  private static final String LIKE_ESCAPE_CHARACTER = "\\";
  private static final int UUID_TEXT_LENGTH = 36;
  // 시·도 대체 이름 구분자
  private static final String SI_DO_ALTERNATIVE_DELIMITER_REGEX = "\\|";

  public static PushCampaignTargetQuery build(PushCampaignPurpose purpose, PushCampaignTargetType targetType,
      PushCampaignTargetFilter targetFilter, LocalDateTime now) {
    PushCampaignTargetFilter filter = targetFilter == null ? PushCampaignTargetFilter.empty() : targetFilter;
    List<String> conditions = new ArrayList<>();
    Map<String, Object> parameters = new HashMap<>();

    // 대상 종류별 기본 조건
    switch (targetType) {
      case TEST -> appendTestMemberCondition(filter, conditions, parameters);
      case MEMBERS -> appendMemberIdentifierConditions(filter, conditions, parameters);
      case ALL -> conditions.add(ACTIVE_MEMBER_CLAUSE);
      case SEGMENT -> {
        conditions.add(ACTIVE_MEMBER_CLAUSE);
        appendSegmentConditions(filter, now, conditions, parameters);
      }
    }

    // 광고성 > 마케팅 수신 동의자만 (TEST 는 관리자 확인용이라 제외)
    boolean isTestTarget = targetType == PushCampaignTargetType.TEST;
    if (purpose == PushCampaignPurpose.ADVERTISEMENT && !isTestTarget) {
      conditions.add(MARKETING_AGREED_CLAUSE);
    }

    String pushConsentClause = isTestTarget ? ALWAYS_TRUE_CLAUSE
        : purpose == PushCampaignPurpose.ADVERTISEMENT ? MARKETING_AGREED_CLAUSE
        : CONTENT_NOTIFICATION_AGREED_CLAUSE;
    boolean isDeviceFiltered = targetType == PushCampaignTargetType.SEGMENT && filter.deviceType() != null;
    return new PushCampaignTargetQuery(String.join(" AND ", conditions), pushConsentClause, parameters,
        isDeviceFiltered ? filter.deviceType() : null);
  }

  // TEST: 관리자 본인 1명 (ID 없음 > 대상 없음)
  private static void appendTestMemberCondition(PushCampaignTargetFilter filter, List<String> conditions,
      Map<String, Object> parameters) {
    if (filter.testMemberId() == null) {
      conditions.add(ALWAYS_FALSE_CLAUSE);
      return;
    }
    conditions.add("m.member_id = :testMemberId");
    parameters.put("testMemberId", filter.testMemberId());
  }

  // MEMBERS: 식별자 > UUID 는 회원 ID, 나머지는 이메일·닉네임 일치 (식별자 없음 > 대상 없음)
  private static void appendMemberIdentifierConditions(PushCampaignTargetFilter filter, List<String> conditions,
      Map<String, Object> parameters) {
    List<UUID> identifierMemberIds = new ArrayList<>();
    List<String> identifierTexts = new ArrayList<>();
    if (filter.memberIdentifiers() != null) {
      for (String memberIdentifier : filter.memberIdentifiers()) {
        if (!StringUtils.hasText(memberIdentifier)) {
          continue;
        }
        String trimmedIdentifier = memberIdentifier.trim();
        UUID parsedMemberId = parseUuidOrNull(trimmedIdentifier);
        if (parsedMemberId != null) {
          identifierMemberIds.add(parsedMemberId);
        } else {
          identifierTexts.add(trimmedIdentifier);
        }
      }
    }
    List<String> identifierConditions = new ArrayList<>();
    if (!identifierMemberIds.isEmpty()) {
      identifierConditions.add("m.member_id IN (:identifierMemberIds)");
      parameters.put("identifierMemberIds", identifierMemberIds);
    }
    if (!identifierTexts.isEmpty()) {
      identifierConditions.add("m.email IN (:identifierTexts)");
      identifierConditions.add("m.nickname IN (:identifierTexts)");
      parameters.put("identifierTexts", identifierTexts);
    }
    conditions.add(NOT_DELETED_CLAUSE);
    conditions.add(identifierConditions.isEmpty() ? ALWAYS_FALSE_CLAUSE
        : "(" + String.join(" OR ", identifierConditions) + ")");
  }

  // SEGMENT 조건 6개 (입력된 것만)
  private static void appendSegmentConditions(PushCampaignTargetFilter filter, LocalDateTime now,
      List<String> conditions, Map<String, Object> parameters) {
    if (filter.deviceType() != null) {
      // fcm_token.device_type: smallint 순번 (FcmToken 엔티티 ORDINAL)
      conditions.add("EXISTS (SELECT 1 FROM fcm_token f_device WHERE f_device.member_member_id = m.member_id"
          + " AND f_device.device_type = :deviceTypeOrdinal)");
      parameters.put("deviceTypeOrdinal", filter.deviceType().ordinal());
    }
    if (filter.isMarketingAgreed() != null) {
      conditions.add("m.is_marketing_info_agreed = :isMarketingAgreed");
      parameters.put("isMarketingAgreed", filter.isMarketingAgreed());
    }
    if (filter.activeWithinDays() != null) {
      conditions.add("m.last_active_at >= :activeSinceAt");
      parameters.put("activeSinceAt", now.minusDays(filter.activeWithinDays()));
    }
    if (filter.inactiveForDays() != null) {
      conditions.add("(m.last_active_at IS NULL OR m.last_active_at < :inactiveBeforeAt)");
      parameters.put("inactiveBeforeAt", now.minusDays(filter.inactiveForDays()));
    }
    if (filter.joinedFromDate() != null) {
      conditions.add("m.created_date >= :joinedFromAt");
      parameters.put("joinedFromAt", filter.joinedFromDate().atStartOfDay());
    }
    if (filter.joinedToDate() != null) {
      // 종료일 포함 > 다음 날 0시 미만
      conditions.add("m.created_date < :joinedToExclusiveAt");
      parameters.put("joinedToExclusiveAt", filter.joinedToDate().plusDays(1).atStartOfDay());
    }
    if (StringUtils.hasText(filter.siDo())) {
      appendSiDoCondition(filter.siDo(), conditions, parameters);
    }
  }

  // 시·도 앞부분 일치: "|" 로 옛·새 이름 여러 개 (예: 전북|전라북도), 하나라도 일치
  private static void appendSiDoCondition(String siDoText, List<String> conditions, Map<String, Object> parameters) {
    List<String> siDoPrefixConditions = new ArrayList<>();
    for (String siDoAlternative : siDoText.split(SI_DO_ALTERNATIVE_DELIMITER_REGEX)) {
      if (!StringUtils.hasText(siDoAlternative)) {
        continue;
      }
      String parameterName = "siDoPrefix" + siDoPrefixConditions.size();
      siDoPrefixConditions.add("ml.si_do LIKE :" + parameterName + " ESCAPE '" + LIKE_ESCAPE_CHARACTER + "'");
      parameters.put(parameterName, escapeLikePattern(siDoAlternative.trim()) + "%");
    }
    if (siDoPrefixConditions.isEmpty()) {
      return;
    }
    conditions.add("EXISTS (SELECT 1 FROM member_location ml WHERE ml.member_member_id = m.member_id AND ("
        + String.join(" OR ", siDoPrefixConditions) + "))");
  }

  // LIKE 특수문자(\ % _) 이스케이프
  static String escapeLikePattern(String rawText) {
    return rawText.replace(LIKE_ESCAPE_CHARACTER, LIKE_ESCAPE_CHARACTER + LIKE_ESCAPE_CHARACTER)
        .replace("%", LIKE_ESCAPE_CHARACTER + "%")
        .replace("_", LIKE_ESCAPE_CHARACTER + "_");
  }

  // 표준 UUID 표기(36자)만 회원 ID 로 봄 (짧은 하이픈 닉네임 오인 방지)
  private static UUID parseUuidOrNull(String identifierText) {
    if (identifierText.length() != UUID_TEXT_LENGTH) {
      return null;
    }
    try {
      return UUID.fromString(identifierText);
    } catch (IllegalArgumentException notUuidException) {
      return null;
    }
  }
}
