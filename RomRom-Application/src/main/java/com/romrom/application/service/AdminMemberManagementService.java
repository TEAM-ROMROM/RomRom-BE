package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberBlock;
import com.romrom.member.repository.MemberBlockRepository;
import com.romrom.member.repository.MemberRepository;
import com.romrom.notification.entity.FcmToken;
import com.romrom.notification.repository.FcmTokenRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 관리자 회원 운영 관리: 차단 관계 조회·해제, 기기 FCM 토큰 조회·삭제, 프로필(닉네임·사진) 수정
 * 제재·탈퇴는 AdminMemberSanctionService 담당
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMemberManagementService {

  // 관리자 닉네임 입력 상한 (DB 컬럼은 길이 제한 없음)
  private static final int MAXIMUM_NICKNAME_LENGTH = 20;

  private final MemberRepository memberRepository;
  private final MemberBlockRepository memberBlockRepository;
  private final FcmTokenRepository fcmTokenRepository;
  private final AdminMemberSanctionService adminMemberSanctionService;

  /**
   * 회원 차단 관계 2방향 (회원이 차단한 + 회원을 차단한)
   */
  @Transactional(readOnly = true)
  public AdminResponse getMemberBlocks(AdminRequest request) {
    UUID targetMemberId = requireMemberId(request);
    List<MemberBlock> memberBlocks = memberBlockRepository.findAllInvolvingMember(targetMemberId);
    return AdminResponse.builder().memberBlocks(memberBlocks).build();
  }

  /**
   * 차단 관계 1건 해제
   */
  @Transactional
  public AdminResponse deleteMemberBlock(AdminRequest request) {
    if (request.getMemberBlockId() == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    MemberBlock memberBlock = memberBlockRepository.findById(request.getMemberBlockId())
        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
    memberBlockRepository.delete(memberBlock);
    log.info("[admin member] block deleted : memberBlockId={}, executorAdminId={}",
        request.getMemberBlockId(), request.getExecutorAdminId());
    return AdminResponse.builder().build();
  }

  /**
   * 회원 기기별 FCM 토큰 (푸시 미수신 확인용)
   */
  @Transactional(readOnly = true)
  public AdminResponse getMemberFcmTokens(AdminRequest request) {
    UUID targetMemberId = requireMemberId(request);
    List<FcmToken> fcmTokens = fcmTokenRepository.findAllByMemberIdForAdmin(targetMemberId);
    return AdminResponse.builder().fcmTokens(fcmTokens).build();
  }

  /**
   * FCM 토큰 1건 삭제 (만료·잘못된 토큰 정리, 앱 재실행 시 다시 등록됨)
   */
  @Transactional
  public AdminResponse deleteFcmToken(AdminRequest request) {
    if (request.getFcmTokenId() == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    FcmToken fcmToken = fcmTokenRepository.findById(request.getFcmTokenId())
        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
    fcmTokenRepository.delete(fcmToken);
    log.info("[admin member] fcm token deleted : fcmTokenId={}, executorAdminId={}",
        request.getFcmTokenId(), request.getExecutorAdminId());
    return AdminResponse.builder().build();
  }

  /**
   * 프로필 수정: 대상 검증 > 닉네임 변경 (길이·중복) > 사진 초기화
   */
  @Transactional
  public AdminResponse updateMemberProfile(AdminRequest request) {
    adminMemberSanctionService.validateAdminAction(requireMemberId(request), request.getExecutorAdminId());
    Member targetMember = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    // 닉네임 변경 (공백·같은 값이면 건너뜀)
    String requestedNickname = request.getMemberNickname() != null ? request.getMemberNickname().trim() : "";
    if (StringUtils.hasText(requestedNickname) && !requestedNickname.equals(targetMember.getNickname())) {
      if (requestedNickname.length() > MAXIMUM_NICKNAME_LENGTH) {
        throw new CustomException(ErrorCode.INVALID_REQUEST);
      }
      if (Boolean.TRUE.equals(memberRepository.existsByNicknameAndMemberIdNot(requestedNickname, targetMember.getMemberId()))) {
        throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
      }
      log.info("[admin member] nickname changed : memberId={}, {} -> {}",
          targetMember.getMemberId(), targetMember.getNickname(), requestedNickname);
      targetMember.setNickname(requestedNickname);
    }

    // 프로필 사진 초기화 (null > 앱 기본 이미지)
    if (Boolean.TRUE.equals(request.getIsProfileImageReset())) {
      log.info("[admin member] profile image reset : memberId={}", targetMember.getMemberId());
      targetMember.setProfileUrl(null);
    }

    memberRepository.save(targetMember);
    return AdminResponse.builder().member(targetMember).build();
  }

  private UUID requireMemberId(AdminRequest request) {
    if (request.getMemberId() == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    return request.getMemberId();
  }
}
