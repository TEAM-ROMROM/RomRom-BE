package com.romrom.common.util;

import com.github.javafaker.Faker;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommonUtil {

  private static final Faker faker = new Faker();

  // 모든 유니코드 글자 + 숫자 + 공백 허용
  private static final Pattern SPECIAL_CHARS = Pattern.compile("[^\\p{L}\\p{N}\\s]");

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

  /**
   * null 문자 처리 -> str1이 null 인 경우 str2 반환
   * "null" 문자열 처리 -> str1이 "null" 인 경우 str2 반환
   * str1이 빈 문자열 or 공백인 경우 -> str2 반환
   *
   * @param str1 검증할 문자열
   * @param str2 str1 이 null 인경우 반환할 문자열
   * @return null 이 아닌 문자열
   */
  public static String nvl(String str1, String str2) {
    if (str1 == null) { // str1 이 null 인 경우
      return str2;
    } else if (str1.equals("null")) { // str1 이 문자열 "null" 인 경우
      return str2;
    } else if (str1.isBlank()) { // str1 이 "" or " " 인 경우
      return str2;
    }
    return str1;
  }

  /**
   * Integer val 값이 null 인 경우 0으로 변환 후 반환
   *
   * @param val 검증할 Integer 래퍼클래스 정수 val
   * @return null 이 아닌 정수 값
   */
  public static int null2ZeroInt(Integer val) {
    if (val == null) { // val 이 null 인경우 0 반환
      return 0;
    }
    return val;
  }

  /**
   * 특수문자 제거
   * 영숫자 (a-z, A-Z, 0-9)와 공백을 제외한 모든 값을 제거합니다
   */
  public static String normalizeAndRemoveSpecialCharacters(String input) {
    return normalize(input, "");
  }

  /**
   * 특수문자 변환
   * 영숫자 (a-z, A-Z, 0-9)와 공백을 제외한 모든 값을 원하는 값으로 변환합니다.
   */
  public static String normalizeAndReplaceSpecialCharacters(String input, String replacement) {
    return normalize(input, replacement);
  }

  /**
   * Unicode 정규화
   * 텍스트 내 모든 특수문자 (문자(letter), 숫자(number), 공백 제외) 제거/치환
   * 연속 공백 -> 단일 공백
   * trim()
   *
   * @param input              정규화 할 문자열
   * @param specialReplacement 특수문자를 치환할 문자열 (제거 시 "" 입력, 치환 시 원하는 문자열 입력)
   * @return 정규화 된 문자열
   */
  private static String normalize(String input, String specialReplacement) {
    return Optional.ofNullable(input)
        .filter(s -> !s.isBlank())
        .map(s -> Normalizer.normalize(s, Normalizer.Form.NFKC)) // Unicode 정규화
        .map(s -> SPECIAL_CHARS.matcher(s).replaceAll(specialReplacement)) // 특수문자 제거/치환
        .map(s -> s.replaceAll("\\s+", " ").trim()) // 공백 정리 & trim
        .orElse("");
  }

  /**
   * trim + 여러 공백을 단일 공백으로 축약
   */
  public static String normalizeSpaces(String input) {
    if (input == null) {
      return "";
    }
    return input.trim().replaceAll("\\s+", " ");
  }

  /**
   * List<Double>을 float[]로 변환
   * SUH-AIder 임베딩 응답 변환용
   *
   * @param doubleList Double 리스트
   * @return float 배열
   */
  public static float[] convertDoubleListToFloatArray(List<Double> doubleList) {
    if (doubleList == null || doubleList.isEmpty()) {
      return new float[0];
    }
    float[] result = new float[doubleList.size()];
    for (int i = 0; i < doubleList.size(); i++) {
      result[i] = doubleList.get(i).floatValue();
    }
    return result;
  }

}
