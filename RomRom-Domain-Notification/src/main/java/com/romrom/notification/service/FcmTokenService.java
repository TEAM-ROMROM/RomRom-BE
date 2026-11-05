package com.romrom.notification.service;

import com.romrom.common.constant.NotificationConstants;
import com.romrom.member.entity.Member;
import com.romrom.member.service.MemberService;
import com.romrom.notification.dto.NotificationRequest;
import com.romrom.notification.entity.FcmToken;
import com.romrom.notification.repository.FcmTokenRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * FCM 토큰 관리 (#874): 토큰 값 기준 저장(갱신·다중 기기·계정 전환), 로그아웃 삭제, 오래된 토큰 정리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmTokenService {

  private final FcmTokenRepository fcmTokenRepository;
  private final MemberService memberService;

  /**
   * 토큰 저장: 같은 토큰 행 있으면 회원·기기·갱신일 갱신, 없으면 새 행 (동시 등록 충돌 > 1회 재시도)
   * 메서드 전체 트랜잭션 없음: 저장 충돌 시 rollback-only 로 응답이 실패하지 않게 저장 호출 단위로 커밋
   */
  public void saveFcmToken(NotificationRequest request) {
    if (!StringUtils.hasText(request.getFcmToken()) || request.getMember() == null) {
      log.warn("[fcm token] save skipped : empty token or member");
      return;
    }
    try {
      upsertFcmToken(request);
    } catch (DataIntegrityViolationException duplicateTokenException) {
      // 같은 토큰 동시 등록 > 먼저 들어간 행을 갱신
      upsertFcmToken(request);
    }
  }

  // 토큰 값 조회 > 갱신 또는 생성
  private void upsertFcmToken(NotificationRequest request) {
    String fcmTokenValue = request.getFcmToken().trim();
    Member requestMember = request.getMember();
    FcmToken fcmToken = fcmTokenRepository.findFirstByTokenOrderByUpdatedDateDesc(fcmTokenValue)
        .orElseGet(() -> FcmToken.builder().token(fcmTokenValue).build());

    boolean isAccountSwitched = fcmToken.getMember() != null
        && !fcmToken.getMember().getMemberId().equals(requestMember.getMemberId());
    if (isAccountSwitched) {
      log.info("[fcm token] moved to other member : fromMemberId={}, toMemberId={}",
          fcmToken.getMember().getMemberId(), requestMember.getMemberId());
    }
    fcmToken.setMember(requestMember);
    if (request.getDeviceType() != null) {
      fcmToken.setDeviceType(request.getDeviceType());
    }
    // 값이 같아도 갱신일 변경 (오래된 토큰 정리 기준)
    fcmToken.setUpdatedDate(LocalDateTime.now());
    fcmTokenRepository.save(fcmToken);
    log.debug("[fcm token] saved : memberId={}, deviceType={}", requestMember.getMemberId(), fcmToken.getDeviceType());
  }

  /**
   * 특정 회원의 FCM 토큰 조회
   */
  public List<FcmToken> findAllTokensByMemberId(UUID memberId) {
    Member member = memberService.findMemberById(memberId);
    return fcmTokenRepository.findAllByMember(member);
  }

  /**
   * 로그아웃 토큰 삭제: 본인 소유 토큰 1개만 (토큰 미전달 옛 앱 > 아무것도 안 함) > 삭제 수
   */
  public int deleteTokenOfMember(UUID memberId, String fcmTokenValue) {
    if (memberId == null || !StringUtils.hasText(fcmTokenValue)) {
      return 0;
    }
    int deletedTokenCount = fcmTokenRepository.deleteByTokenAndMemberId(fcmTokenValue.trim(), memberId);
    log.debug("[fcm token] logout delete : memberId={}, deleted={}", memberId, deletedTokenCount);
    return deletedTokenCount;
  }

  /**
   * 갱신 후 90일 지난 토큰 삭제 > 삭제 수
   */
  public int deleteStaleTokens(LocalDateTime now) {
    int deletedTokenCount = fcmTokenRepository.deleteAllByUpdatedDateBefore(
        now.minusDays(NotificationConstants.FCM_TOKEN_STALE_DAYS));
    log.info("[fcm token cleanup] deleted stale tokens : count={}", deletedTokenCount);
    return deletedTokenCount;
  }
}
