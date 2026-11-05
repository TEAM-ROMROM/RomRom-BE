package com.romrom.item.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.romrom.common.constant.AccountStatus;
import com.romrom.item.event.ItemRegisteredEvent;
import com.romrom.item.service.KeywordAlertTargetSelector.KeywordAlertTarget;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberBlock;
import com.romrom.member.entity.MemberKeywordAlert;
import com.romrom.member.repository.MemberBlockRepository;
import com.romrom.member.repository.MemberKeywordAlertRepository;
import com.romrom.member.repository.MemberLocationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 키워드 알림 대상 조회 단위 테스트 (#873): 차단 양방향 제외, 후보 없음 > 추가 조회 없음
 */
@ExtendWith(MockitoExtension.class)
class KeywordAlertMatchingServiceTest {

  private static final Member OWNER_MEMBER =
      Member.builder().memberId(UUID.randomUUID()).accountStatus(AccountStatus.ACTIVE_ACCOUNT).build();

  private static final ItemRegisteredEvent REGISTERED_ITEM = new ItemRegisteredEvent(
      UUID.randomUUID(), OWNER_MEMBER.getMemberId(), "아이패드 미니 6", "배터리 효율 95%", null, null);

  @Mock
  private MemberKeywordAlertRepository memberKeywordAlertRepository;

  @Mock
  private MemberLocationRepository memberLocationRepository;

  @Mock
  private MemberBlockRepository memberBlockRepository;

  @InjectMocks
  private KeywordAlertMatchingService keywordAlertMatchingService;

  private static Member createActiveMember() {
    return Member.builder().memberId(UUID.randomUUID()).accountStatus(AccountStatus.ACTIVE_ACCOUNT).build();
  }

  private static MemberKeywordAlert createKeywordAlert(Member member) {
    return MemberKeywordAlert.builder().member(member).keyword("아이패드").normalizedKeyword("아이패드").build();
  }

  @Test
  @DisplayName("주인이 차단한 회원·주인을 차단한 회원 모두 제외")
  void findKeywordAlertTargets_차단양방향제외() {
    Member blockedByOwnerMember = createActiveMember();
    Member blockingOwnerMember = createActiveMember();
    Member normalMember = createActiveMember();
    when(memberKeywordAlertRepository.findAllContainedIn(anyString())).thenReturn(List.of(
        createKeywordAlert(blockedByOwnerMember),
        createKeywordAlert(blockingOwnerMember),
        createKeywordAlert(normalMember)));
    when(memberLocationRepository.findByMemberMemberIdIn(any())).thenReturn(List.of());
    when(memberBlockRepository.findAllBlockRelations(eq(OWNER_MEMBER.getMemberId()), any())).thenReturn(List.of(
        MemberBlock.builder().blockerMember(OWNER_MEMBER).blockedMember(blockedByOwnerMember).build(),
        MemberBlock.builder().blockerMember(blockingOwnerMember).blockedMember(OWNER_MEMBER).build()));

    List<KeywordAlertTarget> keywordAlertTargets = keywordAlertMatchingService.findKeywordAlertTargets(REGISTERED_ITEM);

    Assertions.assertEquals(List.of(new KeywordAlertTarget(normalMember.getMemberId(), "아이패드")), keywordAlertTargets);
  }

  @Test
  @DisplayName("매칭 키워드가 주인 것뿐이면 빈 결과, 위치·차단 조회 생략")
  void findKeywordAlertTargets_주인키워드만_조회생략() {
    when(memberKeywordAlertRepository.findAllContainedIn(anyString()))
        .thenReturn(List.of(createKeywordAlert(OWNER_MEMBER)));

    Assertions.assertTrue(keywordAlertMatchingService.findKeywordAlertTargets(REGISTERED_ITEM).isEmpty());
    verifyNoInteractions(memberLocationRepository, memberBlockRepository);
  }

  @Test
  @DisplayName("매칭 문구: 이름 + 설명 정규화 값으로 조회")
  void findKeywordAlertTargets_매칭문구() {
    when(memberKeywordAlertRepository.findAllContainedIn("아이패드 미니 6 배터리 효율 95%")).thenReturn(List.of());

    Assertions.assertTrue(keywordAlertMatchingService.findKeywordAlertTargets(REGISTERED_ITEM).isEmpty());
    verifyNoInteractions(memberLocationRepository, memberBlockRepository);
  }
}
