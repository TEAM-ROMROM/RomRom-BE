package com.romrom.common.constant;

/**
 * 동네 인증 방식 2개: 실제 GPS 위치, 서비스 지역 밖 지도 직접 선택 (#858)
 */
public enum LocationVerificationMethod {
  // 실제 GPS 위치 (서비스 지역 안)
  GPS,
  // 실제 GPS가 서비스 지역(대한민국) 밖 > 지도에서 동네 선택 (해외 심사 대응)
  MANUAL_OUTSIDE_SERVICE_AREA
}
