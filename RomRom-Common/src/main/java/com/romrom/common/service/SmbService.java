package com.romrom.common.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.util.FileUtil;
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

  @Value("${file.dir}")
  private String dir;

  /**
   * SMB 파일 업로드 (단일)
   *
   * @return 업로드 된 파일 Path
   */
  @Override
  @Transactional
  public String uploadFile(MultipartFile file) {

    try {
      // 1. 파일 유효성 검사
      FileUtil.validateFile(file);

      // 2. 파일 이름 설정
      String fileName = FileUtil.generateFilename(file);
      String filePath = FileUtil.combineBaseAndPath(dir, fileName);

      // 3. InputStream 생성
      try (InputStream inputStream = file.getInputStream()) {
        // 4. 메시지 생성 및 헤더 설정
        Message<InputStream> message = MessageBuilder
            .withPayload(inputStream)
            .setHeader("file_name", fileName)
            .build();
        log.debug("SMB 메시지 생성 완료: {}", message);

        // 5. 파일 업로드
        log.debug("SMB 파일 업로드 시작: 파일명={}, 크기={} 바이트", fileName, file.getSize());
        smbUploadChannel.send(message);
        log.debug("SMB 파일 업로드 성공: {}", fileName);

        return filePath;
      }
    } catch (Exception e) {
      log.error("SMB 파일 업로드 실패: 파일명={}", file.getOriginalFilename(), e);
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
      log.error("파일 삭제 실패: 파일명={}, 오류={}", filePath, e.getMessage());
      throw new CustomException(ErrorCode.FILE_DELETE_ERROR);
    }
  }
}
