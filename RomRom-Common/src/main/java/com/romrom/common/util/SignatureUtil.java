package com.romrom.common.util;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

/**
 * HMAC-SHA256 서명 생성 및 검증 유틸리티
 */
@Slf4j
public class SignatureUtil {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  /**
   * HMAC-SHA256 서명 생성
   *
   * @param timestamp 타임스탬프 문자열
   * @param secretKey Secret Key
   * @return Hex 인코딩된 HMAC-SHA256 서명
   */
  public static String generateSignature(String timestamp, String secretKey) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      SecretKeySpec keySpec = new SecretKeySpec(
          secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
      mac.init(keySpec);
      byte[] hash = mac.doFinal(timestamp.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hash);
    } catch (Exception e) {
      log.error("서명 생성 실패: {}", e.getMessage());
      throw new CustomException(ErrorCode.INVALID_SIGNATURE);
    }
  }

  /**
   * HMAC-SHA256 서명 검증 (constant-time 비교로 timing attack 방지)
   *
   * @param timestamp 타임스탬프 문자열
   * @param secretKey Secret Key
   * @param signature 클라이언트가 전달한 서명
   * @return 서명 일치 여부
   */
  public static boolean verifySignature(String timestamp, String secretKey, String signature) {
    String expected = generateSignature(timestamp, secretKey);
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        signature.getBytes(StandardCharsets.UTF_8)
    );
  }

  /**
   * 바이트 배열을 Hex 문자열로 변환
   */
  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
