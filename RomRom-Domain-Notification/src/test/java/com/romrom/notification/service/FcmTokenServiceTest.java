package com.romrom.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.common.constant.DeviceType;
import com.romrom.member.entity.Member;
import com.romrom.member.service.MemberService;
import com.romrom.notification.dto.NotificationRequest;
import com.romrom.notification.entity.FcmToken;
import com.romrom.notification.repository.FcmTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * FCM 토큰 저장·삭제 단위 테스트 (#874): 갱신, 계정 전환, 다중 기기, 동시 등록 재시도, 로그아웃 삭제, 오래된 토큰
 */
class FcmTokenServiceTest {

  private FcmTokenRepository fcmTokenRepository;
  private FcmTokenService fcmTokenService;

  @BeforeEach
  void setUp() {
    fcmTokenRepository = mock(FcmTokenRepository.class);
    fcmTokenService = new FcmTokenService(fcmTokenRepository, mock(MemberService.class));
  }

  private static Member createMember() {
    return Member.builder().memberId(UUID.randomUUID()).build();
  }

  private static NotificationRequest createRequest(Member member, String fcmTokenValue, DeviceType deviceType) {
    return NotificationRequest.builder().member(member).fcmToken(fcmTokenValue).deviceType(deviceType).build();
  }

  private FcmToken captureSavedToken() {
    ArgumentCaptor<FcmToken> savedTokenCaptor = ArgumentCaptor.forClass(FcmToken.class);
    verify(fcmTokenRepository).save(savedTokenCaptor.capture());
    return savedTokenCaptor.getValue();
  }

  @Test
  @DisplayName("새 토큰(갱신된 토큰 포함)은 토큰 값 그대로 새 행 저장")
  void saveFcmToken_새토큰_새행저장() {
    Member member = createMember();
    when(fcmTokenRepository.findFirstByTokenOrderByUpdatedDateDesc("refreshed-token")).thenReturn(Optional.empty());

    fcmTokenService.saveFcmToken(createRequest(member, " refreshed-token ", DeviceType.ANDROID));

    FcmToken savedToken = captureSavedToken();
    Assertions.assertEquals("refreshed-token", savedToken.getToken());
    Assertions.assertEquals(member, savedToken.getMember());
    Assertions.assertEquals(DeviceType.ANDROID, savedToken.getDeviceType());
    Assertions.assertNotNull(savedToken.getUpdatedDate());
  }

  @Test
  @DisplayName("같은 토큰 재등록 > 같은 행 갱신일만 갱신 (새 행 없음)")
  void saveFcmToken_같은토큰_갱신일갱신() {
    Member member = createMember();
    LocalDateTime oldUpdatedDate = LocalDateTime.now().minusDays(80);
    FcmToken existingToken = FcmToken.builder().fcmTokenId(UUID.randomUUID()).member(member).token("same-token")
        .deviceType(DeviceType.IOS).updatedDate(oldUpdatedDate).build();
    when(fcmTokenRepository.findFirstByTokenOrderByUpdatedDateDesc("same-token")).thenReturn(Optional.of(existingToken));

    fcmTokenService.saveFcmToken(createRequest(member, "same-token", DeviceType.IOS));

    FcmToken savedToken = captureSavedToken();
    Assertions.assertSame(existingToken, savedToken);
    Assertions.assertTrue(savedToken.getUpdatedDate().isAfter(oldUpdatedDate));
  }

  @Test
  @DisplayName("같은 토큰이 다른 계정으로 들어오면 그 계정으로 이동")
  void saveFcmToken_계정전환_회원이동() {
    Member previousMember = createMember();
    Member newMember = createMember();
    FcmToken existingToken = FcmToken.builder().member(previousMember).token("shared-device-token")
        .deviceType(DeviceType.ANDROID).build();
    when(fcmTokenRepository.findFirstByTokenOrderByUpdatedDateDesc("shared-device-token"))
        .thenReturn(Optional.of(existingToken));

    fcmTokenService.saveFcmToken(createRequest(newMember, "shared-device-token", DeviceType.ANDROID));

    Assertions.assertEquals(newMember, captureSavedToken().getMember());
  }

  @Test
  @DisplayName("한 회원의 두 번째 기기 토큰은 기존 기기 토큰을 덮지 않고 별도 행")
  void saveFcmToken_다중기기_별도행() {
    Member member = createMember();
    when(fcmTokenRepository.findFirstByTokenOrderByUpdatedDateDesc("tablet-token")).thenReturn(Optional.empty());

    fcmTokenService.saveFcmToken(createRequest(member, "tablet-token", DeviceType.IOS));

    FcmToken savedToken = captureSavedToken();
    Assertions.assertNull(savedToken.getFcmTokenId());
    Assertions.assertEquals("tablet-token", savedToken.getToken());
  }

  @Test
  @DisplayName("동시 등록 unique 충돌 > 다시 조회해 갱신")
  void saveFcmToken_동시등록충돌_재시도() {
    Member member = createMember();
    FcmToken concurrentlyInsertedToken = FcmToken.builder().member(member).token("race-token").build();
    when(fcmTokenRepository.findFirstByTokenOrderByUpdatedDateDesc("race-token"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(concurrentlyInsertedToken));
    when(fcmTokenRepository.save(any(FcmToken.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"))
        .thenReturn(concurrentlyInsertedToken);

    fcmTokenService.saveFcmToken(createRequest(member, "race-token", DeviceType.ANDROID));

    verify(fcmTokenRepository, times(2)).save(any(FcmToken.class));
  }

  @Test
  @DisplayName("빈 토큰은 저장하지 않음")
  void saveFcmToken_빈토큰_무시() {
    fcmTokenService.saveFcmToken(createRequest(createMember(), "  ", DeviceType.ANDROID));
    verify(fcmTokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("로그아웃: 본인 소유 해당 토큰만 삭제, 토큰 미전달(옛 앱)이면 삭제 없음")
  void deleteTokenOfMember_로그아웃삭제() {
    UUID memberId = UUID.randomUUID();
    when(fcmTokenRepository.deleteByTokenAndMemberId("device-token", memberId)).thenReturn(1);

    Assertions.assertEquals(1, fcmTokenService.deleteTokenOfMember(memberId, " device-token "));
    Assertions.assertEquals(0, fcmTokenService.deleteTokenOfMember(memberId, null));
    Assertions.assertEquals(0, fcmTokenService.deleteTokenOfMember(memberId, ""));
    verify(fcmTokenRepository, times(1)).deleteByTokenAndMemberId(anyString(), any());
  }

  @Test
  @DisplayName("오래된 토큰 정리 기준: 현재 - 90일")
  void deleteStaleTokens_90일기준() {
    LocalDateTime now = LocalDateTime.of(2026, 11, 5, 4, 30);
    fcmTokenService.deleteStaleTokens(now);
    verify(fcmTokenRepository).deleteAllByUpdatedDateBefore(now.minusDays(90));
  }
}
