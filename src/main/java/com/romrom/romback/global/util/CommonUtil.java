package com.romrom.romback.global.util;

import com.github.javafaker.Faker;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.UUID;

public class CommonUtil {
  private static final Faker faker = new Faker();

  public static String getRandomName() {
    return faker.funnyName().name() + "-" + UUID.randomUUID().toString().substring(0, 5);
  }

  /**
   * 문자열 SHA-256 해시 계산
   */
  public static String calculateSha256ByStr(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hashBytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException("SHA-256 해시 계산 실패", e);
    }
  }

  /**
   * 파일 SHA-256 해시값 계산
   */
  public static String calculateFileHash(Path filePath) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] fileBytes = Files.readAllBytes(filePath);
      byte[] hashBytes = digest.digest(fileBytes);
      StringBuilder sb = new StringBuilder();
      for (byte b : hashBytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException("파일 해시 계산 실패", e);
    }
  }

}
