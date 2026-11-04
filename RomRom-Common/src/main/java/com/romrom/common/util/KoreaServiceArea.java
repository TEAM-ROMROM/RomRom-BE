package com.romrom.common.util;

import lombok.experimental.UtilityClass;

/**
 * 서비스 지역(대한민국) 대략 범위: 위도 33.0~38.7, 경도 124.5~131.9
 * 앱 lib/services/gps_location_verifier.dart koreaServiceAreaBounds 와 같은 값 (함께 변경)
 */
@UtilityClass
public class KoreaServiceArea {

  public static final double MINIMUM_LATITUDE = 33.0;
  public static final double MAXIMUM_LATITUDE = 38.7;
  public static final double MINIMUM_LONGITUDE = 124.5;
  public static final double MAXIMUM_LONGITUDE = 131.9;

  /**
   * 좌표가 서비스 지역 안인지 (좌표 없음 > false)
   */
  public boolean contains(Double latitude, Double longitude) {
    if (latitude == null || longitude == null) {
      return false;
    }
    return latitude >= MINIMUM_LATITUDE && latitude <= MAXIMUM_LATITUDE
        && longitude >= MINIMUM_LONGITUDE && longitude <= MAXIMUM_LONGITUDE;
  }
}
