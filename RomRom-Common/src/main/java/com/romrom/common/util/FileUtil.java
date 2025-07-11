package com.romrom.common.util;

import com.romrom.common.constant.MimeType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.romrom.common.util.CommonUtil.*;

@Slf4j
@UtilityClass
public class FileUtil {

  /**
   * 파일 유효성 검증
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
   * @param originalFilename 원본 파일 명
   * @return ex) 1711326597434_fileName.png
   */
  public String generateFilename(String originalFilename) {
    String timeStamp = String.valueOf(System.currentTimeMillis());

    if (!StringUtils.hasText(originalFilename)) {
      log.debug("originalFilename name이 비어 있어 기본 이름 'image' 사용");
      String fileName = timeStamp + "_" + "image";
      log.debug("최종 파일 명 생성: {}", fileName);
      return fileName;
    }

    // 파일 확장자 분리
    String extension = "";
    String baseName = originalFilename;
    int dotIndex = originalFilename.lastIndexOf(".");
    if (dotIndex != -1) {
      extension = originalFilename.substring(dotIndex);
      baseName = originalFilename.substring(0, dotIndex);
    }
    log.debug("원본 파일명: {}", originalFilename);
    log.debug("확장자: {}", extension);
    log.debug("Base name: {}", baseName);

    // 특수문자 제거
    String normalized = normalizeAndRemoveSpecialCharacters(baseName);
    log.debug("normalizeAndRemoveSpecialCharacters 적용 후: {}", normalized);

    // 공백 -> 언더바
    String encodedName = normalized.replaceAll("\\s+", "_");
    log.debug("Safe name (공백 -> _): {}", encodedName);

    // URL encoding (한글 및 비ASCII 문자 보존)
    //String encodedName = URLEncoder.encode(safeName, StandardCharsets.UTF_8);
    //log.debug("URL Encoded name: {}", encodedName);

    if (encodedName.isEmpty()) {
      encodedName = "image";
      log.debug("Encoded name이 비어 있어 기본 이름 'image' 사용");
    }

    String fileName = timeStamp + "_" + encodedName + extension;
    log.debug("최종 파일 명 생성: {}", fileName);
    return fileName;
  }


  /**
   * SMB root-dir, dir에 따른 파일 경로 생성
   */
  public String generateSmbFilePath(String... parts) {
    return "/" + String.join("/", parts);
  }

  public String generateFtpFilePath(String... parts) {
    return "/" + String.join("/", parts);
  }
}
