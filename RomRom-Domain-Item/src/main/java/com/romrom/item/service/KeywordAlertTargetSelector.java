package com.romrom.item.service;

import com.romrom.common.constant.AccountStatus;
import com.romrom.item.event.ItemRegisteredEvent;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberKeywordAlert;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.util.KeywordAlertRule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 키워드 알림 대상 선택 (#873): 키워드 포함 확인 > 대상 제외 > 회원당 1건 (먼저 등록한 키워드 문구)
 * 제외 4개: 물품 주인, 차단 관계(양방향), 탈퇴·정지 회원, 탐색 범위 밖 (위치 없는 회원·물품은 거리 조건 없음)
 */
public final class KeywordAlertTargetSelector {

  // PostGIS ST_DistanceSphere 와 같은 지구 반지름 (미터, 홈 피드 반경 판정과 일치)
  static final double EARTH_RADIUS_IN_METERS = 6370986.0;

  /**
   * 알림 대상 1명: 회원 ID + 문구에 쓸 키워드(표시용)
   */
  public record KeywordAlertTarget(UUID memberId, String keyword) {
  }

  private KeywordAlertTargetSelector() {
  }

  /**
   * 대상 선택
   *
   * @param matchedKeywordAlerts     키워드 후보 (오래된 등록순, 회원 포함)
   * @param memberLocationByMemberId 후보 회원 위치 (없으면 거리 조건 없음)
   * @param blockRelatedMemberIds    물품 주인과 차단 관계인 회원 (양방향)
   */
  public static List<KeywordAlertTarget> selectTargets(
      ItemRegisteredEvent registeredItem,
      List<MemberKeywordAlert> matchedKeywordAlerts,
      Map<UUID, MemberLocation> memberLocationByMemberId,
      Set<UUID> blockRelatedMemberIds
  ) {
    String matchingText = KeywordAlertRule.toMatchingText(registeredItem.itemName(), registeredItem.itemDescription());
    Set<UUID> selectedMemberIds = new HashSet<>();
    List<KeywordAlertTarget> keywordAlertTargets = new ArrayList<>();

    for (MemberKeywordAlert keywordAlert : matchedKeywordAlerts) {
      Member keywordOwnerMember = keywordAlert.getMember();
      UUID memberId = keywordOwnerMember.getMemberId();
      boolean isTarget = !selectedMemberIds.contains(memberId)
          && KeywordAlertRule.isKeywordContained(matchingText, keywordAlert.getNormalizedKeyword())
          && !memberId.equals(registeredItem.ownerMemberId())
          && !blockRelatedMemberIds.contains(memberId)
          && isNotifiableMember(keywordOwnerMember)
          && isWithinSearchRadius(registeredItem, memberLocationByMemberId.get(memberId),
          keywordOwnerMember.getSearchRadiusInMeters());
      if (isTarget) {
        selectedMemberIds.add(memberId);
        keywordAlertTargets.add(new KeywordAlertTarget(memberId, keywordAlert.getKeyword()));
      }
    }
    return keywordAlertTargets;
  }

  /**
   * 알림 받을 수 있는 회원 여부 (탈퇴·정지 제외)
   */
  static boolean isNotifiableMember(Member member) {
    if (Boolean.TRUE.equals(member.getIsDeleted())) {
      return false;
    }
    AccountStatus accountStatus = member.getAccountStatus();
    return accountStatus != AccountStatus.DELETE_ACCOUNT && accountStatus != AccountStatus.SUSPENDED_ACCOUNT;
  }

  /**
   * 회원 탐색 범위 안 물품 여부 (회원·물품 위치 없음 > 통과, 반경 없음 > 기본 10km)
   */
  static boolean isWithinSearchRadius(ItemRegisteredEvent registeredItem, MemberLocation memberLocation,
      Double searchRadiusInMeters) {
    if (memberLocation == null || memberLocation.getLatitude() == null || memberLocation.getLongitude() == null
        || registeredItem.latitude() == null || registeredItem.longitude() == null) {
      return true;
    }
    double radiusInMeters = searchRadiusInMeters != null ? searchRadiusInMeters : Member.DEFAULT_SEARCH_RADIUS_IN_METERS;
    double distanceInMeters = calculateDistanceInMeters(
        memberLocation.getLatitude(), memberLocation.getLongitude(),
        registeredItem.latitude(), registeredItem.longitude());
    return distanceInMeters <= radiusInMeters;
  }

  /**
   * 두 좌표 사이 구면 거리 (미터, haversine)
   */
  static double calculateDistanceInMeters(double fromLatitude, double fromLongitude, double toLatitude,
      double toLongitude) {
    double latitudeDifferenceRadians = Math.toRadians(toLatitude - fromLatitude);
    double longitudeDifferenceRadians = Math.toRadians(toLongitude - fromLongitude);
    double haversineValue = Math.pow(Math.sin(latitudeDifferenceRadians / 2), 2)
        + Math.cos(Math.toRadians(fromLatitude)) * Math.cos(Math.toRadians(toLatitude))
        * Math.pow(Math.sin(longitudeDifferenceRadians / 2), 2);
    return 2 * EARTH_RADIUS_IN_METERS * Math.asin(Math.min(1.0, Math.sqrt(haversineValue)));
  }
}
