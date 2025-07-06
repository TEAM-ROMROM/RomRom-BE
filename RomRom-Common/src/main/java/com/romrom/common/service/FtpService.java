package com.romrom.common.service;

import com.romrom.common.constant.MimeType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.util.FileUtil;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import static com.romrom.common.util.CommonUtil.nvl;

@Service
@RequiredArgsConstructor
@Slf4j
public class FtpService {

  private final MessageChannel ftpUploadChannel;
  private final MessageChannel ftpDeleteChannel;
  @Qualifier("applicationTaskExecutor")
  private final TaskExecutor taskExecutor;

  @Value("${ftp.root-dir}")
  private String rootDir;

  @Value("${ftp.dir}")
  private String dir;

  private static final int UPLOAD_FILE_MAX_COUNT = 10;

  /**
   * FTP 파일 업로드 (다중)
   */
  @Transactional
  public CompletableFuture<List<String>> uploadFile(List<MultipartFile> files) {
    if (files.size() > UPLOAD_FILE_MAX_COUNT || files.isEmpty()) {
      log.error("파일 개수 오류: 최대 {}, 요청 {}", UPLOAD_FILE_MAX_COUNT, files.size());
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }
    int requestFileCount = files.size();
    List<CompletableFuture<String>> futures = new ArrayList<>();

    for (MultipartFile file : files) {
      CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
        try {
          return uploadFile(file);
        } catch (Exception e) {
          log.error("멀티스레드 업로드 중 오류: {}", e.getMessage());
          throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
        }
      }, taskExecutor);
      futures.add(future);
    }

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(result -> {
        List<String> successfulUploads = new ArrayList<>();
        boolean failureOccurred = false;
        for (CompletableFuture<String> future : futures) {
          try {
            successfulUploads.add(future.get());
          } catch (Exception e) {
            log.error("비동기 업로드 실패: {}", e.getMessage());
            failureOccurred = true;
          }
        }
        if (failureOccurred || successfulUploads.size() != requestFileCount) {
          log.error("업로드 실패: 요청={}, 성공={}", requestFileCount, successfulUploads.size());
          deleteFiles(successfulUploads);
          throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
        }
        log.debug("FTP 업로드 완료: {}개 성공", successfulUploads.size());
        return successfulUploads;
      })
      .exceptionally(throwable -> {
        log.error("전체 업로드 오류: {}", throwable.getMessage());
        throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
      });
  }

  /**
   * FTP 파일 업로드 (단일)
   */
  @Transactional
  public String uploadFile(MultipartFile file) {
    validateFile(file);
    try {
      String fileName = FileUtil.generateFilename(file.getOriginalFilename());
      String filePath = FileUtil.generateFtpFilePath(dir, fileName);

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
   */
  public void deleteFiles(List<String> fileNames) {
    log.debug("FTP 파일 삭제 요청: {}개", fileNames.size());
    if (fileNames.isEmpty()) {
      log.warn("삭제할 파일 리스트가 비어있습니다.");
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }

    for (String fileName : fileNames) {
      try {
        Message<String> deleteMessage = MessageBuilder
          .withPayload(fileName)
          .setHeader("file_name", fileName)
          .build();
        ftpDeleteChannel.send(deleteMessage);
        log.debug("FTP 파일 삭제 성공: {}", fileName);
      } catch (Exception e) {
        log.error("FTP 파일 삭제 실패: {}", fileName, e);
        throw new CustomException(ErrorCode.FILE_DELETE_ERROR);
      }
    }
  }

  /**
   * 업로드된 파일 유효성 검사
   */
  private void validateFile(MultipartFile file) {
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
      log.error("유효하지 않은 Mime 타입입니다.");
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }
    if (!MimeType.isValidImageMimeType(mimeType)) {
      log.error("이미지 파일이 아닙니다");
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }

    log.debug("파일 검증 성공: {}, MIME={}", file.getOriginalFilename(), mimeType);
  }
}
