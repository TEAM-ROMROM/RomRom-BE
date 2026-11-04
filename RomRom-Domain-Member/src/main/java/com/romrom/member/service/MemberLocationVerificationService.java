package com.romrom.member.service;

import com.romrom.common.constant.LocationVerificationMethod;
import com.romrom.common.constant.SystemConfigKeys;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.service.SystemConfigCacheService;
import com.romrom.common.util.KoreaServiceArea;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberLocationRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 동네 인증 (#858): 위치 저장 시 인증 기록, 인증 필요 기능 진입 확인
 * 진입 거부는 system_config location.verification.enforced = "true" 일 때만 (기본 꺼짐 > 구버전 앱 영향 없음)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MemberLocationVerificationService {

  private static final String ENFORCED_CONFIG_VALUE = "true";

  private final MemberLocationRepository memberLocationRepository;
  private final SystemConfigCacheService systemConfigCacheService;

  /**
   * 위치 저장 요청의 인증 방식 > 인증 기록 반영
   * 방식 없음(구버전 앱·인증 없는 저장) > 미인증, GPS > 좌표가 서비스 지역 안,
   * MANUAL_OUTSIDE_SERVICE_AREA > 실제 GPS 좌표가 서비스 지역 밖 + 고른 좌표는 서비스 지역 안
   */
  public void applyVerification(MemberLocation memberLocation, MemberRequest request) {
    LocationVerificationMethod verificationMethod = request.getLocationVerificationMethod();
    if (verificationMethod == null) {
      memberLocation.setLocationVerifiedAt(null);
      memberLocation.setLocationVerificationMethod(null);
      return;
    }
    validateVerificationLocation(verificationMethod, request);
    memberLocation.setLocationVerifiedAt(LocalDateTime.now());
    memberLocation.setLocationVerificationMethod(verificationMethod);
  }

  /**
   * 인증 필요 기능 진입 확인 (스위치 꺼짐 > 통과, 켜짐 + 미인증 > 403 LOCATION_NOT_VERIFIED)
   */
  public void verifyLocationVerified(UUID memberId) {
    if (!isVerificationEnforced()) {
      return;
    }
    boolean isLocationVerified = memberLocationRepository.findByMemberMemberId(memberId)
        .map(MemberLocation::getIsLocationVerified)
        .orElse(false);
    if (!isLocationVerified) {
      log.debug("[location verification] rejected 403 : not verified, memberId={}", memberId);
      throw new CustomException(ErrorCode.LOCATION_NOT_VERIFIED);
    }
  }

  // 스위치 값 "true" 만 켜짐 (없음·그 외 값 > 꺼짐)
  private boolean isVerificationEnforced() {
    return ENFORCED_CONFIG_VALUE.equalsIgnoreCase(
        systemConfigCacheService.getOrDefault(SystemConfigKeys.LOCATION_VERIFICATION_ENFORCED, "").trim());
  }

  private void validateVerificationLocation(LocationVerificationMethod verificationMethod, MemberRequest request) {
    boolean isSelectedLocationInServiceArea = KoreaServiceArea.contains(request.getLatitude(), request.getLongitude());
    boolean isVerificationLocationValid = switch (verificationMethod) {
      case GPS -> isSelectedLocationInServiceArea;
      case MANUAL_OUTSIDE_SERVICE_AREA -> isSelectedLocationInServiceArea
          && request.getGpsLatitude() != null
          && request.getGpsLongitude() != null
          && !KoreaServiceArea.contains(request.getGpsLatitude(), request.getGpsLongitude());
    };
    if (!isVerificationLocationValid) {
      log.debug("[location verification] rejected 400 : invalid location, method={}", verificationMethod);
      throw new CustomException(ErrorCode.INVALID_LOCATION_VERIFICATION);
    }
  }
}
