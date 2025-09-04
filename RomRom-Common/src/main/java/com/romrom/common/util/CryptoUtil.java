package com.romrom.common.util;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

/**
 * 암호화/복호화 유틸리티 클래스
 * AES-GCM 256bit 알고리즘을 사용한 비밀번호 암호화/복호화 기능 제공
 */
@Slf4j
public class CryptoUtil {

  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH = 16;

  /**
   * 비밀번호를 AES-GCM 알고리즘으로 암호화
   * 
   * @param password 암호화할 비밀번호
   * @param secretKey 암호화 키 (Base64 인코딩된 문자열)
   * @return 암호화된 비밀번호 (Base64 인코딩)
   */
  public static String encryptPassword(String password, String secretKey) {
    try {
      // 키 디코딩
      byte[] keyBytes = Base64.getDecoder().decode(secretKey);
      SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
      
      // IV 생성
      byte[] iv = new byte[GCM_IV_LENGTH];
      new SecureRandom().nextBytes(iv);
      
      // 암호화
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
      
      byte[] encryptedData = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
      
      // IV + 암호화된 데이터 결합
      byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedData.length];
      System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
      System.arraycopy(encryptedData, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedData.length);
      
      return Base64.getEncoder().encodeToString(encryptedWithIv);
      
    } catch (Exception e) {
      log.error("비밀번호 암호화 실패: {}", e.getMessage());
      throw new RuntimeException("비밀번호 암호화에 실패했습니다.", e);
    }
  }

  /**
   * 암호화된 비밀번호를 복호화
   * 
   * @param encryptedPassword 암호화된 비밀번호 (Base64 인코딩)
   * @param secretKey 복호화 키 (Base64 인코딩된 문자열)
   * @return 복호화된 비밀번호
   */
  public static String decryptPassword(String encryptedPassword, String secretKey) {
    try {
      // 암호화된 데이터 디코딩
      byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedPassword);
      
      // IV와 암호화된 데이터 분리
      byte[] iv = new byte[GCM_IV_LENGTH];
      byte[] encryptedData = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
      System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);
      System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);
      
      // 키 디코딩
      byte[] keyBytes = Base64.getDecoder().decode(secretKey);
      SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
      
      // 복호화
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
      cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
      
      byte[] decryptedData = cipher.doFinal(encryptedData);
      return new String(decryptedData, StandardCharsets.UTF_8);
      
    } catch (Exception e) {
      log.error("비밀번호 복호화 실패: {}", e.getMessage());
      throw new RuntimeException("비밀번호 복호화에 실패했습니다.", e);
    }
  }

  /**
   * 새로운 AES 키 생성 (256bit)
   * 
   * @return Base64로 인코딩된 AES 키
   */
  public static String generateAESKey() {
    try {
      KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
      keyGenerator.init(256);
      SecretKey secretKey = keyGenerator.generateKey();
      return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    } catch (Exception e) {
      log.error("AES 키 생성 실패: {}", e.getMessage());
      throw new RuntimeException("AES 키 생성에 실패했습니다.", e);
    }
  }
}