package com.romrom.common.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.util.FileUtil;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
@Primary
public class FtpService implements FileService {

  private final MessageChannel ftpUploadChannel;
  private final MessageChannel ftpDeleteChannel;

  @Value("${file.dir}")
  private String dir;

  /**
   * FTP 파일 업로드 (단일)
   *
   * @return filePath 파일 경로 /romrom/images/example.png
   */
  @Override
  @Transactional
  public String uploadFile(MultipartFile file) {
    FileUtil.validateFile(file);
    try {
      String fileName = FileUtil.generateFilename(file); // "1293847_image.png"
      String filePath = FileUtil.combineBaseAndPath(dir, fileName); // "/romrom/images/1293847_image.png"

      log.debug("FTP 파일 업로드 시작: 파일명={}, 크기={} 바이트", fileName, file.getSize());
      try (InputStream inputStream = file.getInputStream()) {
        Message<InputStream> message = MessageBuilder
            .withPayload(inputStream)
            .setHeader("file_name", fileName)
            .build();
        log.debug("FTP 메시지 생성 완료: {}", message);

        ftpUploadChannel.send(message);
        log.debug("FTP 파일 업로드 성공: {}", fileName);
        return filePath;
      }
    } catch (Exception e) {
      log.error("FTP 파일 업로드 실패: {}", file.getOriginalFilename(), e);
      throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
    }
  }

  /**
   * FTP 파일 삭제
   *
   * @param filePath 파일 경로 /romrom/images/example.png
   */
  @Override
  @Transactional
  public void deleteFile(String filePath) {
    try {
      Message<String> deleteMessage = MessageBuilder
          .withPayload(filePath)
          .setHeader("file_name", filePath)
          .build();
      ftpDeleteChannel.send(deleteMessage);
      log.debug("FTP 파일 삭제 성공: {}", filePath);
    } catch (Exception e) {
      log.warn("FTP 파일 삭제 실패: {}", filePath, e);
    }
  }
}
