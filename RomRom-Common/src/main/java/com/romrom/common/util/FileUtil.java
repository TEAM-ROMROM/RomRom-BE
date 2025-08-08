package com.romrom.common.util;

import static com.romrom.common.util.CommonUtil.nvl;

import com.romrom.common.constant.MimeType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@UtilityClass
public class FileUtil {

  // 허용대상: 유니코드 글자, 숫자, 점(.), 공백
  private static final Pattern SPECIAL_CHARS = Pattern.compile("[^\\p{L}\\p{N}\\.\\s]");

  /**
   * 파일 유효성 검증
   *
   * @param file 파일
   */
  public void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      log.error("요청된 파일이 비어있습니다");
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }

    String mimeType = file.getContentType();
    if (nvl(mimeType, "").isEmpty()) {
      log.error("Mime 타입이 비어있습니다.");
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }
    if (!MimeType.isValidMimeType(mimeType)) {
      log.error("유효하지 않은 Mime 타입입니다. 요청된 MimeType: {}", mimeType);
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }
    if (!MimeType.isValidImageMimeType(mimeType)) {
      log.error("이미지 파일이 아닙니다. 요청된 MimeType: {}", mimeType);
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }

    log.debug("파일 검증 성공: {}, MIME={}", file.getOriginalFilename(), mimeType);
  }

  /**
   * 파일 명 생성
   *
   * @param file MultipartFile
   * @return ex) 1711326597434_fileName.png
   */
  public String generateFilename(MultipartFile file) {
    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null || originalFilename.isEmpty()) {
      log.error("요청한 파일의 파일명이 존재하지 않습니다");
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }

    String timeStamp = String.valueOf(System.currentTimeMillis());
    String normalizedOriginalFilename = normalizeFileName(originalFilename, "");

    String fileName = timeStamp + "_" + normalizedOriginalFilename;
    log.debug("최종 파일 명 생성: {}", fileName);
    return fileName;
  }

  /**
   * BASE URL과 경로를 결합합니다.
   *
   * @param baseUrl 베이스 URL (후행 슬래시 제거)
   * @param path    경로
   * @return 결합된 URL
   */
  public String combineBaseAndPath(String baseUrl, String path) {
    String base = removeTrailingSlash(baseUrl);
    if (path == null || path.isEmpty()) {
      return base;
    }
    if (!path.startsWith("/")) {
      return base + "/" + path;
    }
    return base + path;
  }

  /**
   * URL 또는 경로 문자열의 끝에 있는 슬래시('/) 제거
   *
   * @param url 슬래시 제거 대상 문자열
   * @return 후행 슬래시가 제거된 문자열
   */
  public String removeTrailingSlash(String url) {
    if (url == null || url.isEmpty()) {
      return url;
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  /**
   * imageUrl로부터 파일명을 추출합니다
   *
   * @param imageUrl http://example.com/volume1/image.png
   * @return image.png
   */
  public String extractFileName(String imageUrl) {
    return imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
  }

  /**
   * URL에서 파일 경로를 추출합니다
   *
   * @param baseUrl 기본 URL (도메인, 예: http://example.com)
   * @param imageUrl 전체 URL (예: http://example.com/volume1/image.png)
   * @return 파일 경로 (예: volume1/image.png)
   */
  public String extractFilePath(String baseUrl, String imageUrl) {
    String base = removeTrailingSlash(baseUrl);
    if (!imageUrl.startsWith(base)) {
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }
    return imageUrl.length() <= base.length() + 1 ? "" : imageUrl.substring(base.length() + 1);
  }

  /**
   * Unicode 정규화
   * 1) NFKC 형태로 정규화
   * 2) 특수문자(글자, 숫자, 점 제외) 제거/치환
   * 3) 앞뒤 공백 제거 후 연속 공백 -> 단일 언더바(_)
   *
   * @param input              정규화 할 문자열
   * @param specialReplacement 특수문자를 치환할 문자열 (제거 시 "" 입력, 치환 시 원하는 문자열 입력)
   * @return 정규화 된 문자열
   */
  private String normalizeFileName(String input, String specialReplacement) {
    return Optional.ofNullable(input)
        .filter(s -> !s.isBlank())
        .map(s -> Normalizer.normalize(s, Normalizer.Form.NFKC)) // Unicode 정규화
        .map(s -> SPECIAL_CHARS.matcher(s).replaceAll(specialReplacement)) // 특수문자 제거/치환
        .map(s -> s.trim().replaceAll("\\s+", "_")) // 공백을 언더바(_)로 변경
        .orElse("");
  }
}
