package com.romrom.notification.service;

import com.romrom.notification.dto.NotificationRequest;
import com.romrom.notification.entity.FcmToken;
import com.romrom.notification.repository.FcmTokenRepository;
import java.util.ArrayList;
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

  @Transactional
  public void saveFcmToken(NotificationRequest request) {
    // DTO → Entity
    FcmToken fcmToken = FcmToken.builder()
        .token(request.getFcmToken())
        .memberId(request.getMember().getMemberId())
        .deviceType(request.getDeviceType())
        .build();

    // Redis 저장 (동일 키 시 덮어쓰기)
    fcmTokenRepository.save(fcmToken);

    log.debug("토큰 저장 완료");
    log.debug("사용자 ID : {}", fcmToken.getMemberId());
    log.debug("사용자 기기 : {}", fcmToken.getDeviceType());
  }

  public List<FcmToken> findAllTokensByMemberId(UUID memberId) {
    return fcmTokenRepository.findAllByMemberId(memberId);
  }

  public List<FcmToken> findAllTokens() {
    List<FcmToken> fcmTokenList = new ArrayList<>();
    fcmTokenRepository.findAll().forEach(fcmTokenList::add);
    return fcmTokenList;
  }
}
