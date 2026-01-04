package com.romrom.notification.service;

import com.romrom.member.entity.Member;
import com.romrom.member.service.MemberService;
import com.romrom.notification.dto.NotificationRequest;
import com.romrom.notification.entity.FcmToken;
import com.romrom.notification.repository.FcmTokenRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmTokenService {

  private final FcmTokenRepository fcmTokenRepository;
  private final MemberService memberService;

  /**
   * FCM 토큰 저장
   */
  @Transactional
  public void saveFcmToken(NotificationRequest request) {
    // 요청 DeviceType에 저장된 토큰 조회
    FcmToken fcmToken = fcmTokenRepository.findByMemberAndDeviceType(request.getMember(), request.getDeviceType())
      .orElseGet(() -> FcmToken.builder()
        .token(request.getFcmToken())
        .member(request.getMember())
        .deviceType(request.getDeviceType())
        .build()
      );

    fcmTokenRepository.save(fcmToken);

    log.debug("FCM 토큰 저장: 회원: {}, 기기: {}", fcmToken.getMember().getMemberId(), fcmToken.getDeviceType());
  }

  /**
   * 특정 회원의 FCM 토큰 조회
   */
  public List<FcmToken> findAllTokensByMemberId(UUID memberId) {
    Member member = memberService.findMemberById(memberId);
    return fcmTokenRepository.findAllByMember(member);
  }

  /**
   * DB에 저장된 모든 FCM 토큰 조회
   */
  public List<FcmToken> findAllTokens() {
    return fcmTokenRepository.findAll();
  }
}
