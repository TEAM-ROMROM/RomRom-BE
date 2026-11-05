package com.romrom.item.service;

import com.romrom.common.constant.AccountStatus;
import com.romrom.item.event.ItemRegisteredEvent;
import com.romrom.item.service.KeywordAlertTargetSelector.KeywordAlertTarget;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberKeywordAlert;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.util.KeywordAlertRule;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.geolatte.geom.G2D;
import org.geolatte.geom.Point;
import org.geolatte.geom.crs.CoordinateReferenceSystems;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 키워드 알림 대상 선택 단위 테스트 (#873): 주인·차단·탈퇴·정지 제외, 탐색 범위, 회원당 1건, 키워드 포함 재확인
 */
class KeywordAlertTargetSelectorTest {

  // 서울시청
  private static final double ITEM_LATITUDE = 37.5665;
  private static final double ITEM_LONGITUDE = 126.9780;
  // 위도 0.03도 ≈ 3.3km, 0.2도 ≈ 22km
  private static final double NEAR_LATITUDE = ITEM_LATITUDE + 0.03;
  private static final double FAR_LATITUDE = ITEM_LATITUDE + 0.2;

  private static final UUID OWNER_MEMBER_ID = UUID.randomUUID();

  private static final ItemRegisteredEvent REGISTERED_ITEM = new ItemRegisteredEvent(
      UUID.randomUUID(), OWNER_MEMBER_ID, "닌텐도 스위치 OLED", "박스 포함, 조이콘 2개", ITEM_LONGITUDE, ITEM_LATITUDE);

  private static Member createMember(AccountStatus accountStatus, Double searchRadiusInMeters) {
    return Member.builder()
        .memberId(UUID.randomUUID())
        .accountStatus(accountStatus)
        .searchRadiusInMeters(searchRadiusInMeters)
        .build();
  }

  private static Member createActiveMember() {
    return createMember(AccountStatus.ACTIVE_ACCOUNT, null);
  }

  private static MemberKeywordAlert createKeywordAlert(Member member, String keyword) {
    return MemberKeywordAlert.builder()
        .keywordAlertId(UUID.randomUUID())
        .member(member)
        .keyword(KeywordAlertRule.toDisplayKeyword(keyword))
        .normalizedKeyword(KeywordAlertRule.normalize(keyword))
        .build();
  }

  private static MemberLocation createLocation(Member member, double latitude) {
    return MemberLocation.builder()
        .member(member)
        .geom(new Point<>(new G2D(ITEM_LONGITUDE, latitude), CoordinateReferenceSystems.WGS84))
        .build();
  }

  private static List<KeywordAlertTarget> select(List<MemberKeywordAlert> keywordAlerts,
      Map<UUID, MemberLocation> memberLocationByMemberId, Set<UUID> blockRelatedMemberIds) {
    return KeywordAlertTargetSelector.selectTargets(
        REGISTERED_ITEM, keywordAlerts, memberLocationByMemberId, blockRelatedMemberIds);
  }

  @Test
  @DisplayName("키워드가 이름·설명에 포함될 때만 대상 (미포함 > 제외), 문구는 표시용 키워드")
  void selectTargets_키워드포함_대상() {
    Member keywordMember = createActiveMember();

    List<KeywordAlertTarget> keywordAlertTargets =
        select(List.of(createKeywordAlert(keywordMember, "  닌텐도   Switch")), Map.of(), Set.of());
    List<KeywordAlertTarget> containedTargets =
        select(List.of(createKeywordAlert(keywordMember, "스위치 OLED")), Map.of(), Set.of());

    Assertions.assertTrue(keywordAlertTargets.isEmpty());
    Assertions.assertEquals(List.of(new KeywordAlertTarget(keywordMember.getMemberId(), "스위치 OLED")), containedTargets);
  }

  @Test
  @DisplayName("물품 주인 본인 키워드 > 제외")
  void selectTargets_주인제외() {
    Member ownerMember = Member.builder().memberId(OWNER_MEMBER_ID).accountStatus(AccountStatus.ACTIVE_ACCOUNT).build();

    Assertions.assertTrue(select(List.of(createKeywordAlert(ownerMember, "닌텐도")), Map.of(), Set.of()).isEmpty());
  }

  @Test
  @DisplayName("차단 관계 회원 > 제외 (양방향은 조회 단계에서 합친 집합)")
  void selectTargets_차단제외() {
    Member blockedMember = createActiveMember();
    Member normalMember = createActiveMember();

    List<KeywordAlertTarget> keywordAlertTargets = select(
        List.of(createKeywordAlert(blockedMember, "닌텐도"), createKeywordAlert(normalMember, "닌텐도")),
        Map.of(), Set.of(blockedMember.getMemberId()));

    Assertions.assertEquals(List.of(new KeywordAlertTarget(normalMember.getMemberId(), "닌텐도")), keywordAlertTargets);
  }

  @Test
  @DisplayName("탈퇴(상태·삭제 플래그)·정지 회원 > 제외")
  void selectTargets_탈퇴정지제외() {
    Member suspendedMember = createMember(AccountStatus.SUSPENDED_ACCOUNT, null);
    Member withdrawnMember = createMember(AccountStatus.DELETE_ACCOUNT, null);
    Member deletedFlagMember = createActiveMember();
    deletedFlagMember.setIsDeleted(true);

    List<KeywordAlertTarget> keywordAlertTargets = select(List.of(
        createKeywordAlert(suspendedMember, "닌텐도"),
        createKeywordAlert(withdrawnMember, "닌텐도"),
        createKeywordAlert(deletedFlagMember, "닌텐도")), Map.of(), Set.of());

    Assertions.assertTrue(keywordAlertTargets.isEmpty());
  }

  @Test
  @DisplayName("탐색 범위: 반경 안 대상, 반경 밖 제외, 반경 미설정은 기본 10km")
  void selectTargets_탐색범위() {
    Member nearMember = createMember(AccountStatus.ACTIVE_ACCOUNT, 5000.0);
    Member farMember = createMember(AccountStatus.ACTIVE_ACCOUNT, 5000.0);
    Member defaultRadiusNearMember = createActiveMember();
    Member defaultRadiusFarMember = createActiveMember();

    List<KeywordAlertTarget> keywordAlertTargets = select(
        List.of(
            createKeywordAlert(nearMember, "닌텐도"),
            createKeywordAlert(farMember, "닌텐도"),
            createKeywordAlert(defaultRadiusNearMember, "닌텐도"),
            createKeywordAlert(defaultRadiusFarMember, "닌텐도")),
        Map.of(
            nearMember.getMemberId(), createLocation(nearMember, NEAR_LATITUDE),
            farMember.getMemberId(), createLocation(farMember, FAR_LATITUDE),
            defaultRadiusNearMember.getMemberId(), createLocation(defaultRadiusNearMember, NEAR_LATITUDE),
            defaultRadiusFarMember.getMemberId(), createLocation(defaultRadiusFarMember, FAR_LATITUDE)),
        Set.of());

    Assertions.assertEquals(
        List.of(nearMember.getMemberId(), defaultRadiusNearMember.getMemberId()),
        keywordAlertTargets.stream().map(KeywordAlertTarget::memberId).toList());
  }

  @Test
  @DisplayName("위치 없는 회원·위치 없는 물품 > 거리 조건 없음")
  void selectTargets_위치없음_거리조건없음() {
    Member noLocationMember = createMember(AccountStatus.ACTIVE_ACCOUNT, 1000.0);
    Member farMember = createMember(AccountStatus.ACTIVE_ACCOUNT, 1000.0);
    ItemRegisteredEvent noLocationItem = new ItemRegisteredEvent(
        UUID.randomUUID(), OWNER_MEMBER_ID, "닌텐도 스위치", null, null, null);

    List<KeywordAlertTarget> noMemberLocationTargets =
        select(List.of(createKeywordAlert(noLocationMember, "닌텐도")), Map.of(), Set.of());
    List<KeywordAlertTarget> noItemLocationTargets = KeywordAlertTargetSelector.selectTargets(
        noLocationItem,
        List.of(createKeywordAlert(farMember, "닌텐도")),
        Map.of(farMember.getMemberId(), createLocation(farMember, FAR_LATITUDE)),
        Set.of());

    Assertions.assertEquals(1, noMemberLocationTargets.size());
    Assertions.assertEquals(1, noItemLocationTargets.size());
  }

  @Test
  @DisplayName("한 회원 키워드 여러 개 매칭 > 알림 1건, 먼저 등록한 키워드 문구")
  void selectTargets_회원당1건() {
    Member keywordMember = createActiveMember();
    Member otherMember = createActiveMember();

    List<KeywordAlertTarget> keywordAlertTargets = select(List.of(
        createKeywordAlert(keywordMember, "스위치"),
        createKeywordAlert(otherMember, "조이콘"),
        createKeywordAlert(keywordMember, "닌텐도"),
        createKeywordAlert(keywordMember, "OLED")), Map.of(), Set.of());

    Assertions.assertEquals(List.of(
        new KeywordAlertTarget(keywordMember.getMemberId(), "스위치"),
        new KeywordAlertTarget(otherMember.getMemberId(), "조이콘")), keywordAlertTargets);
  }

  @Test
  @DisplayName("거리 계산: 위도 0.03도 ≈ 3.3km")
  void calculateDistanceInMeters_위도차이() {
    double distanceInMeters = KeywordAlertTargetSelector.calculateDistanceInMeters(
        ITEM_LATITUDE, ITEM_LONGITUDE, NEAR_LATITUDE, ITEM_LONGITUDE);

    Assertions.assertEquals(3336.0, distanceInMeters, 5.0);
  }
}
