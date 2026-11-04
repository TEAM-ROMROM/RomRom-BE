package com.romrom.member.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.romrom.common.constant.LocationVerificationMethod;
import com.romrom.common.constant.SystemConfigKeys;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.service.SystemConfigCacheService;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberLocationRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 동네 인증 단위 테스트 (#858): 방식별 위치 검증·기록, 스위치 꺼짐 통과, 켜짐 + 미인증 403
 */
@ExtendWith(MockitoExtension.class)
class MemberLocationVerificationServiceTest {

  // 서울시청 (서비스 지역 안)
  private static final double SEOUL_LATITUDE = 37.5665;
  private static final double SEOUL_LONGITUDE = 126.9780;
  // 미국 쿠퍼티노 (서비스 지역 밖)
  private static final double CUPERTINO_LATITUDE = 37.3230;
  private static final double CUPERTINO_LONGITUDE = -122.0322;

  @Mock
  private MemberLocationRepository memberLocationRepository;

  @Mock
  private SystemConfigCacheService systemConfigCacheService;

  @InjectMocks
  private MemberLocationVerificationService memberLocationVerificationService;

  private MemberRequest createLocationRequest(LocationVerificationMethod method, Double gpsLatitude, Double gpsLongitude) {
    return MemberRequest.builder()
        .latitude(SEOUL_LATITUDE)
        .longitude(SEOUL_LONGITUDE)
        .locationVerificationMethod(method)
        .gpsLatitude(gpsLatitude)
        .gpsLongitude(gpsLongitude)
        .build();
  }

  @Test
  @DisplayName("인증 방식 없음(구버전 앱) > 미인증으로 기록")
  void applyVerification_방식없음_미인증() {
    MemberLocation memberLocation = MemberLocation.builder()
        .locationVerifiedAt(LocalDateTime.now())
        .locationVerificationMethod(LocationVerificationMethod.GPS)
        .build();

    memberLocationVerificationService.applyVerification(memberLocation, createLocationRequest(null, null, null));

    Assertions.assertNull(memberLocation.getLocationVerifiedAt());
    Assertions.assertNull(memberLocation.getLocationVerificationMethod());
    Assertions.assertFalse(memberLocation.getIsLocationVerified());
  }

  @Test
  @DisplayName("GPS + 서비스 지역 안 좌표 > 인증 시각·방식 기록")
  void applyVerification_GPS_인증() {
    MemberLocation memberLocation = new MemberLocation();

    memberLocationVerificationService.applyVerification(
        memberLocation, createLocationRequest(LocationVerificationMethod.GPS, null, null));

    Assertions.assertTrue(memberLocation.getIsLocationVerified());
    Assertions.assertEquals(LocationVerificationMethod.GPS, memberLocation.getLocationVerificationMethod());
  }

  @Test
  @DisplayName("서비스 지역 밖 지도 선택 + 실제 GPS 국내 > INVALID_LOCATION_VERIFICATION")
  void applyVerification_지도선택_실제GPS국내_거부() {
    CustomException customException = Assertions.assertThrows(CustomException.class,
        () -> memberLocationVerificationService.applyVerification(new MemberLocation(),
            createLocationRequest(LocationVerificationMethod.MANUAL_OUTSIDE_SERVICE_AREA, SEOUL_LATITUDE, SEOUL_LONGITUDE)));

    Assertions.assertEquals(ErrorCode.INVALID_LOCATION_VERIFICATION, customException.getErrorCode());
  }

  @Test
  @DisplayName("서비스 지역 밖 지도 선택 + 실제 GPS 해외 > 인증")
  void applyVerification_지도선택_실제GPS해외_인증() {
    MemberLocation memberLocation = new MemberLocation();

    memberLocationVerificationService.applyVerification(memberLocation,
        createLocationRequest(LocationVerificationMethod.MANUAL_OUTSIDE_SERVICE_AREA, CUPERTINO_LATITUDE, CUPERTINO_LONGITUDE));

    Assertions.assertTrue(memberLocation.getIsLocationVerified());
    Assertions.assertEquals(LocationVerificationMethod.MANUAL_OUTSIDE_SERVICE_AREA, memberLocation.getLocationVerificationMethod());
  }

  @Test
  @DisplayName("스위치 없음(기본) > 미인증이어도 통과, 위치 조회 안 함")
  void verifyLocationVerified_스위치꺼짐_통과() {
    when(systemConfigCacheService.getOrDefault(SystemConfigKeys.LOCATION_VERIFICATION_ENFORCED, "")).thenReturn("");

    memberLocationVerificationService.verifyLocationVerified(UUID.randomUUID());

    verify(memberLocationRepository, never()).findByMemberMemberId(any());
  }

  @Test
  @DisplayName("스위치 켜짐 + 미인증 위치 > LOCATION_NOT_VERIFIED")
  void verifyLocationVerified_스위치켜짐_미인증_거부() {
    UUID memberId = UUID.randomUUID();
    when(systemConfigCacheService.getOrDefault(SystemConfigKeys.LOCATION_VERIFICATION_ENFORCED, "")).thenReturn("true");
    when(memberLocationRepository.findByMemberMemberId(memberId)).thenReturn(Optional.of(new MemberLocation()));

    CustomException customException = Assertions.assertThrows(CustomException.class,
        () -> memberLocationVerificationService.verifyLocationVerified(memberId));

    Assertions.assertEquals(ErrorCode.LOCATION_NOT_VERIFIED, customException.getErrorCode());
  }

  @Test
  @DisplayName("스위치 켜짐 + 인증 위치 > 통과")
  void verifyLocationVerified_스위치켜짐_인증_통과() {
    UUID memberId = UUID.randomUUID();
    when(systemConfigCacheService.getOrDefault(SystemConfigKeys.LOCATION_VERIFICATION_ENFORCED, "")).thenReturn("true");
    when(memberLocationRepository.findByMemberMemberId(memberId))
        .thenReturn(Optional.of(MemberLocation.builder().locationVerifiedAt(LocalDateTime.now()).build()));

    Assertions.assertDoesNotThrow(() -> memberLocationVerificationService.verifyLocationVerified(memberId));
  }
}
