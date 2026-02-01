package com.romrom.member.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.dto.MemberResponse;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberBlock;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberBlockRepository;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class MemberBlockService {
  private final MemberRepository memberRepository;
  private final MemberBlockRepository memberBlockRepository;
  private final MemberLocationRepository memberLocationRepository;

  @Transactional(readOnly = true)
  public MemberResponse getBlockedMemberList(UUID memberId) {
    // 내가 차단한 내역들 조회 (상대방 정보 포함)
    List<Member> blockedMembers = memberBlockRepository.findAllByBlockerId(memberId)
        .stream()
        .map(MemberBlock::getBlockedMember)
        .toList();

    // 위치 정보 일괄 조회 (N+1 방지)
    Set<UUID> memberIds = blockedMembers.stream()
        .map(Member::getMemberId)
        .collect(Collectors.toSet());

    Map<UUID, MemberLocation> locationMap = memberLocationRepository
        .findByMemberMemberIdIn(memberIds)
        .stream()
        .collect(Collectors.toMap(
            ml -> ml.getMember().getMemberId(),
            ml -> ml
        ));

    // 각 Member에 locationAddress 세팅
    blockedMembers.forEach(member -> {
      MemberLocation location = locationMap.get(member.getMemberId());
      if (location != null) {
        member.setLocationAddress(location.getFullAddress());
      }
    });

    return MemberResponse.builder()
        .members(blockedMembers)
        .build();
  }

  @Transactional
  public void unblockMember(MemberRequest request) {
    UUID memberId = request.getMember().getMemberId();
    Member target = memberRepository.findById(request.getBlockTargetMemberId())
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    memberBlockRepository.deleteByBlockerIdAndBlockedId(memberId, target.getMemberId());
    log.debug("회원 {}님이 회원 {}님을 차단 해제했습니다.", memberId, target.getMemberId());
  }

  @Transactional
  public void blockMember(MemberRequest request) {
    UUID memberId = request.getMember().getMemberId();
    UUID targetId = request.getBlockTargetMemberId();

    // 자기 자신 차단 불가
    if (memberId.equals(targetId)) {
      throw new CustomException(ErrorCode.CANNOT_BLOCK_SELF);
    }

    // 이미 차단한 회원인지 검증
    if (memberBlockRepository.existsBlockBetween(memberId, targetId)) {
      throw new CustomException(ErrorCode.ALREADY_BLOCKED);
    }
    // 차단 대상 존재 여부 검증
    Member target = memberRepository.findById(targetId)
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    MemberBlock block = MemberBlock.builder()
        .blockerMember(request.getMember())
        .blockedMember(target)
        .build();
    memberBlockRepository.save(block);
    log.debug("회원 {}님이 회원 {}님을 차단했습니다.", memberId, targetId);
  }

  public List<MemberBlock> getMemberBlockList(UUID memberId, Set<UUID> targetIds) {
    return memberBlockRepository.findAllBlockRelations(memberId, targetIds);
  }

  // 차단된 회원인지 확인
  public void verifyNotBlocked(UUID memberId1, UUID memberId2) {
    if (memberBlockRepository.existsBlockBetween(memberId1, memberId2)) {
      throw new CustomException(ErrorCode.BLOCKED_MEMBER_INTERACTION);
    }
  }
}
