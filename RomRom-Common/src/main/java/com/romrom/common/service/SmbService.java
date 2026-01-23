package com.romrom.common.service;

import com.romrom.common.dto.CompressedImage;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.util.FileUtil;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmbService implements FileService {

  private final MessageChannel smbUploadChannel;
  private final MessageChannel smbDeleteChannel;
  private final ImageCompressionService imageCompressionService;

  @Value("${file.dir}")
  private String dir;

  /**
   * SMB 파일 업로드 (단일)
   * - WebP 압축 시도 후 실패 시 원본 업로드 (fallback)
   *
   * @return 업로드 된 파일 Path
   */
  @Override
  @Transactional
  public String uploadFile(MultipartFile file) {
    try {
      // 1. 파일 유효성 검사
      FileUtil.validateFile(file);

      // 2. 이미지 압축 시도
      CompressedImage compressed = imageCompressionService.compress(file);

      if (compressed != null) {
        // 압축 성공 → WebP로 업로드
        return uploadCompressedImage(compressed);
      } else {
        // 압축 실패 → 원본 업로드 (fallback)
        log.info("이미지 압축 실패, 원본 파일 업로드로 전환: {}", file.getOriginalFilename());
        return uploadOriginalFile(file);
      }
    } catch (Exception e) {
      log.error("SMB 파일 업로드 실패: 파일명={}", file.getOriginalFilename(), e);
      throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
    }
  }

  /**
   * 압축된 이미지 업로드
   *
   * @param compressed 압축된 이미지 정보
   * @return 업로드된 파일 경로
   */
  private String uploadCompressedImage(CompressedImage compressed) {
    String fileName = FileUtil.generateFilenameFromString(compressed.getFileName());
    String filePath = FileUtil.combineBaseAndPath(dir, fileName);

    log.debug("SMB 압축 이미지 업로드 시작: 파일명={}, 압축 후 크기={} 바이트", fileName, compressed.getCompressedSize());

    try (InputStream inputStream = new ByteArrayInputStream(compressed.getData())) {
      Message<InputStream> message = MessageBuilder
          .withPayload(inputStream)
          .setHeader("file_name", fileName)
          .build();
      log.debug("SMB 메시지 생성 완료: {}", message);

      smbUploadChannel.send(message);
      log.debug("SMB 압축 이미지 업로드 성공: {}", fileName);
      return filePath;
    } catch (Exception e) {
      log.error("SMB 압축 이미지 업로드 실패: {}", fileName, e);
      throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
    }
  }

  /**
   * 원본 파일 업로드 (fallback)
   *
   * @param file 원본 MultipartFile
   * @return 업로드된 파일 경로
   */
  private String uploadOriginalFile(MultipartFile file) {
    String fileName = FileUtil.generateFilename(file);
    String filePath = FileUtil.combineBaseAndPath(dir, fileName);

    log.debug("SMB 원본 파일 업로드 시작: 파일명={}, 크기={} 바이트", fileName, file.getSize());

    try (InputStream inputStream = file.getInputStream()) {
      Message<InputStream> message = MessageBuilder
          .withPayload(inputStream)
          .setHeader("file_name", fileName)
          .build();
      log.debug("SMB 메시지 생성 완료: {}", message);

      smbUploadChannel.send(message);
      log.debug("SMB 원본 파일 업로드 성공: {}", fileName);
      return filePath;
    } catch (Exception e) {
      log.error("SMB 원본 파일 업로드 실패: {}", file.getOriginalFilename(), e);
      throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
    }
  }

  @Override
  @Transactional
  public void deleteFile(String filePath) {
    try {
      Message<String> deleteMessage = MessageBuilder
          .withPayload(filePath)
          .build();
      smbDeleteChannel.send(deleteMessage);
      log.debug("파일 삭제 성공: {}", filePath);
    } catch (Exception e) {
      log.warn("파일 삭제 실패: 파일명={}, 오류={}", filePath, e.getMessage());
    }
  }
}
