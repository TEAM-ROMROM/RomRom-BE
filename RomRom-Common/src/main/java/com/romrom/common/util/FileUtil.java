package com.romrom.common.util;

import static com.romrom.common.util.CommonUtil.nvl;

import com.romrom.common.constant.MimeType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@UtilityClass
@Slf4j
public class FileUtil {

  /**
   * 업로드된 파일 유효성 검사
   * 1. 빈 파일 검사
   * 2. MIME 타입 유효성 검사
   * 3. 이미지 파일 검사
   */
  public void validateFile(MultipartFile file) {

    // 요청된 파일이 비어있는지 확인
    if (file == null || file.isEmpty()) {
      log.error("요청된 파일이 비어있습니다");
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }

    String mimeType = file.getContentType();

    // 요청된 파일 Mime 타입이 비어있는지 검증
    if (nvl(mimeType, "").isEmpty()) {
      log.error("Mime 타입이 비어있습니다.");
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }
    // Mime 타입 유효성 검증
    if (!MimeType.isValidMimeType(mimeType)) {
      log.error("유효하지 않은 Mime 타입입니다.");
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }
    // 이미지 Mime 타입 검증
    if (!MimeType.isValidImageMimeType(mimeType)) {
      log.error("요청된 파일의 Mime 타입이 이미지가 아닙니다");
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }

    log.debug("파일 검증 성공: 파일명={}, MIME 타입={}", file.getOriginalFilename(), mimeType);
  }

  /**
   * 파일 검증 및 원본 파일명 반환
   *
   * @param file 요청된 MultipartFile
   * @return 원본 파일명
   */
  public static String validateAndExtractFilename(MultipartFile file) {
    // 파일 검증
    if (file == null || file.isEmpty()) {
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }

    // 원본 파일 명 검증
    String originalFilename = file.getOriginalFilename();
    if (CommonUtil.nvl(originalFilename, "").isEmpty()) {
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }
    return originalFilename;
  }

  /**
   * 파일 명 생성
   *
   * @param originalFilename 원본 파일 명
   * @return ex) 1711326597434_fileName.png
   */
  public String generateFilename(String originalFilename) {
    String timeStamp = String.valueOf(System.currentTimeMillis());
    String fileName = timeStamp + "_" + originalFilename;
    log.debug("파일 명 생성: {}", fileName);
    return fileName;
  }

  /**
   * root-dir, dir에 따른 파일 경로 생성
   */
  public String generateFilePath(String... parts) {
    return "/" + String.join("/", parts);
  }
}
