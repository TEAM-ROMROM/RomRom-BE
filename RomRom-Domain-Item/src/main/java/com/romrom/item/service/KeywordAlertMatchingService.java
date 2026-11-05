package com.romrom.item.service;

import com.romrom.item.event.ItemRegisteredEvent;
import com.romrom.item.service.KeywordAlertTargetSelector.KeywordAlertTarget;
import com.romrom.member.entity.MemberBlock;
import com.romrom.member.entity.MemberKeywordAlert;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberBlockRepository;
import com.romrom.member.repository.MemberKeywordAlertRepository;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.member.util.KeywordAlertRule;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새 물품 > 키워드 알림 대상 조회 (#873): 키워드 후보 > 후보 위치·차단 관계 일괄 조회 > 대상 선택
 * 발송은 하지 않음 (KeywordAlertEventListener 가 트랜잭션 밖에서 발송)
 */
@Service
@RequiredArgsConstructor
public class KeywordAlertMatchingService {

  private final MemberKeywordAlertRepository memberKeywordAlertRepository;
  private final MemberLocationRepository memberLocationRepository;
  private final MemberBlockRepository memberBlockRepository;

  @Transactional(readOnly = true)
  public List<KeywordAlertTarget> findKeywordAlertTargets(ItemRegisteredEvent registeredItem) {
    // 키워드 후보 (물품 이름 + 설명에 포함, 오래된 등록순)
    String matchingText = KeywordAlertRule.toMatchingText(registeredItem.itemName(), registeredItem.itemDescription());
    List<MemberKeywordAlert> matchedKeywordAlerts = memberKeywordAlertRepository.findAllContainedIn(matchingText);
    Set<UUID> candidateMemberIds = matchedKeywordAlerts.stream()
        .map(keywordAlert -> keywordAlert.getMember().getMemberId())
        .filter(memberId -> !memberId.equals(registeredItem.ownerMemberId()))
        .collect(Collectors.toSet());
    if (candidateMemberIds.isEmpty()) {
      return List.of();
    }

    // 후보 위치·차단 관계 일괄 조회 (N+1 방지)
    Map<UUID, MemberLocation> memberLocationByMemberId = memberLocationRepository
        .findByMemberMemberIdIn(candidateMemberIds)
        .stream()
        .collect(Collectors.toMap(
            memberLocation -> memberLocation.getMember().getMemberId(),
            memberLocation -> memberLocation,
            (firstMemberLocation, duplicateMemberLocation) -> firstMemberLocation));
    Set<UUID> blockRelatedMemberIds = findBlockRelatedMemberIds(registeredItem.ownerMemberId(), candidateMemberIds);

    return KeywordAlertTargetSelector.selectTargets(
        registeredItem, matchedKeywordAlerts, memberLocationByMemberId, blockRelatedMemberIds);
  }

  // 물품 주인과 차단 관계인 후보 (주인이 차단 + 주인을 차단)
  private Set<UUID> findBlockRelatedMemberIds(UUID ownerMemberId, Set<UUID> candidateMemberIds) {
    return memberBlockRepository.findAllBlockRelations(ownerMemberId, candidateMemberIds)
        .stream()
        .map(memberBlock -> toCounterpartMemberId(memberBlock, ownerMemberId))
        .collect(Collectors.toSet());
  }

  private static UUID toCounterpartMemberId(MemberBlock memberBlock, UUID ownerMemberId) {
    UUID blockerMemberId = memberBlock.getBlockerMember().getMemberId();
    return blockerMemberId.equals(ownerMemberId) ? memberBlock.getBlockedMember().getMemberId() : blockerMemberId;
  }
}
