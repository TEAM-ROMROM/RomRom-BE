package com.romrom.common.util;

import lombok.extern.slf4j.Slf4j;

/**
 * 앱 버전 비교 유틸리티 (SemVer: major.minor.patch)
 */
@Slf4j
public class VersionUtil {

  /**
   * 두 버전을 비교합니다.
   * v1 < v2 → 음수, v1 == v2 → 0, v1 > v2 → 양수
   * null 또는 빈 문자열 → 0 반환
   * 파싱 실패 → 0 반환
   */
  public static int compareVersions(String v1, String v2) {
    if (v1 == null || v1.isBlank() || v2 == null || v2.isBlank()) {
      return 0;
    }
    try {
      int[] parts1 = parseVersion(v1);
      int[] parts2 = parseVersion(v2);
      for (int i = 0; i < 3; i++) {
        int diff = parts1[i] - parts2[i];
        if (diff != 0) {
          return diff;
        }
      }
      return 0;
    } catch (Exception e) {
      log.warn("버전 파싱 실패: v1={}, v2={}", v1, v2);
      return 0;
    }
  }

  /**
   * 업데이트 필요 여부
   * currentVersion < version → true
   * version 이 null/빈 문자열 → false (미설정)
   */
  public static boolean isUpdateRequired(String currentVersion, String version) {
    if (version == null || version.isBlank()) {
      return false;
    }
    return compareVersions(currentVersion, version) < 0;
  }

  private static int[] parseVersion(String version) {
    String[] parts = version.trim().split("\\.");
    int[] result = new int[3];
    for (int i = 0; i < Math.min(parts.length, 3); i++) {
      result[i] = Integer.parseInt(parts[i].trim());
    }
    return result;
  }
}
