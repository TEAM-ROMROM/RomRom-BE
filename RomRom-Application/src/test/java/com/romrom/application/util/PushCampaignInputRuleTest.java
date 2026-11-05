package com.romrom.application.util;

import com.romrom.application.dto.AdminPushCampaignRequest;
import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.common.constant.PushCampaignTargetType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자 푸시 입력 검증 단위 테스트 (#874): 딥링크, 이미지, 식별자, 예약 시각, 광고 야간 예약
 */
class PushCampaignInputRuleTest {

  private static ErrorCode errorCodeOf(Runnable validation) {
    return Assertions.assertThrows(CustomException.class, validation::run).getErrorCode();
  }

  @Test
  @DisplayName("딥링크: romrom·https 허용, 공지·물품은 UUID 필수, 그 외 거부, 빈 값 null")
  void toValidatedDeepLink() {
    String announcementDeepLink = "romrom://notice/detail?announcementId=" + UUID.randomUUID();
    Assertions.assertEquals(announcementDeepLink, PushCampaignInputRule.toValidatedDeepLink(" " + announcementDeepLink));
    Assertions.assertEquals("https://romrom.app/event", PushCampaignInputRule.toValidatedDeepLink("https://romrom.app/event"));
    Assertions.assertNull(PushCampaignInputRule.toValidatedDeepLink(" "));
    Assertions.assertEquals(ErrorCode.INVALID_PUSH_DEEP_LINK,
        errorCodeOf(() -> PushCampaignInputRule.toValidatedDeepLink("romrom://item/detail?itemId=abc")));
    Assertions.assertEquals(ErrorCode.INVALID_PUSH_DEEP_LINK,
        errorCodeOf(() -> PushCampaignInputRule.toValidatedDeepLink("http://insecure.example.com")));
    Assertions.assertEquals(ErrorCode.INVALID_PUSH_DEEP_LINK,
        errorCodeOf(() -> PushCampaignInputRule.toValidatedDeepLink("javascript:alert(1)")));
  }

  @Test
  @DisplayName("이미지: https 만 허용")
  void toValidatedImageUrl() {
    Assertions.assertEquals("https://cdn.example.com/a.png", PushCampaignInputRule.toValidatedImageUrl("https://cdn.example.com/a.png"));
    Assertions.assertEquals(ErrorCode.INVALID_PUSH_IMAGE_URL,
        errorCodeOf(() -> PushCampaignInputRule.toValidatedImageUrl("/romrom-logo.png")));
  }

  @Test
  @DisplayName("식별자: 줄바꿈·쉼표 구분, 공백·중복 제거, 0개·1001개 거부")
  void toMemberIdentifiers() {
    Assertions.assertEquals(List.of("a@b.com", "nick", "id-1"),
        PushCampaignInputRule.toMemberIdentifiers("a@b.com\r\nnick, id-1;;nick\n\n"));
    Assertions.assertEquals(ErrorCode.INVALID_PUSH_CAMPAIGN_TARGET,
        errorCodeOf(() -> PushCampaignInputRule.toMemberIdentifiers(" ,\n")));
    String tooManyIdentifiers = IntStream.rangeClosed(1, 1001).mapToObj(index -> "member" + index)
        .collect(Collectors.joining("\n"));
    Assertions.assertEquals(ErrorCode.INVALID_PUSH_CAMPAIGN_TARGET,
        errorCodeOf(() -> PushCampaignInputRule.toMemberIdentifiers(tooManyIdentifiers)));
  }

  @Test
  @DisplayName("SEGMENT: 일수 범위 밖·가입일 역전 거부")
  void toTargetFilter_segment검증() {
    AdminPushCampaignRequest invalidDaysRequest = AdminPushCampaignRequest.builder().activeWithinDays(0).build();
    Assertions.assertEquals(ErrorCode.INVALID_PUSH_CAMPAIGN_TARGET,
        errorCodeOf(() -> PushCampaignInputRule.toTargetFilter(PushCampaignTargetType.SEGMENT, invalidDaysRequest)));
    AdminPushCampaignRequest reversedJoinedRequest = AdminPushCampaignRequest.builder()
        .joinedFromDate("2026-02-01").joinedToDate("2026-01-01").build();
    Assertions.assertEquals(ErrorCode.INVALID_PUSH_CAMPAIGN_TARGET,
        errorCodeOf(() -> PushCampaignInputRule.toTargetFilter(PushCampaignTargetType.SEGMENT, reversedJoinedRequest)));
  }

  @Test
  @DisplayName("예약 시각: 미입력 즉시(null), 과거 거부")
  void toValidatedScheduledAt() {
    LocalDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0);
    Assertions.assertNull(PushCampaignInputRule.toValidatedScheduledAt(null, now));
    Assertions.assertEquals(LocalDateTime.of(2026, 11, 5, 13, 0),
        PushCampaignInputRule.toValidatedScheduledAt("2026-11-05T13:00", now));
    Assertions.assertEquals(ErrorCode.INVALID_PUSH_CAMPAIGN_SCHEDULE,
        errorCodeOf(() -> PushCampaignInputRule.toValidatedScheduledAt("2026-11-05T11:59", now)));
  }

  @Test
  @DisplayName("광고성 예약: 21:00·07:59 거부, 08:00 허용, 정보성은 야간 허용")
  void validateAdvertisementSendTime() {
    Assertions.assertEquals(ErrorCode.ADVERTISEMENT_PUSH_NIGHT_RESTRICTED, errorCodeOf(() ->
        PushCampaignInputRule.validateAdvertisementSendTime(PushCampaignPurpose.ADVERTISEMENT, LocalDateTime.of(2026, 11, 5, 21, 0))));
    Assertions.assertEquals(ErrorCode.ADVERTISEMENT_PUSH_NIGHT_RESTRICTED, errorCodeOf(() ->
        PushCampaignInputRule.validateAdvertisementSendTime(PushCampaignPurpose.ADVERTISEMENT, LocalDateTime.of(2026, 11, 6, 7, 59))));
    Assertions.assertDoesNotThrow(() ->
        PushCampaignInputRule.validateAdvertisementSendTime(PushCampaignPurpose.ADVERTISEMENT, LocalDateTime.of(2026, 11, 6, 8, 0)));
    Assertions.assertDoesNotThrow(() ->
        PushCampaignInputRule.validateAdvertisementSendTime(PushCampaignPurpose.INFORMATION, LocalDateTime.of(2026, 11, 5, 23, 0)));
  }
}
