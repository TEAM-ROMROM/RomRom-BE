package com.romrom.romback.global;

import static com.romrom.romback.global.util.CommonUtil.nvl;

import com.romrom.romback.domain.object.constant.MimeType;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmbService {

  private final MessageChannel smbUploadChannel;
  private final MessageChannel smbDeleteChannel;
  @Qualifier("applicationTaskExecutor")
  private final TaskExecutor taskExecutor;

  private static final int UPLOAD_FILE_MAX_COUNT = 10;

  /**
   * SMB 파일 업로드 (다중)
   */
  @Transactional
  public CompletableFuture<List<String>> uploadFile(List<MultipartFile> files) {

    // 1. 파일 업로드 개수 확인
    if (files.size() > UPLOAD_FILE_MAX_COUNT || files.isEmpty()) {
      log.error("요청된 파일이 비어있거나 최대 업로드 개수를 초과했습니다. 최대 업로드 개수: {}, 요청된 파일 개수: {}", UPLOAD_FILE_MAX_COUNT, files.size());
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }
    int requestFileCount = files.size();

    // 2. 멀티스레드 파일 저장
    List<CompletableFuture<String>> futures = new ArrayList<>();

    for (MultipartFile file : files) {
      CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
        try {
          return uploadFile(file);
        } catch (Exception e) {
          log.error("멀티스레드 파일 업로드 중 오류 발생: {}", e.getMessage());
          throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
        }
      }, taskExecutor);
      futures.add(future);
    }

    // 3. 모든 비동기 작업 결과 확인
    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
        .thenApply(result -> {
          List<String> successfulUploads = new ArrayList<>();
          boolean failureOccurred = false;

          for (CompletableFuture<String> future : futures) {
            try {
              successfulUploads.add(future.get());
            } catch (Exception e) {
              log.error("비동기 파일 업로드 실패: {}", e.getMessage());
              failureOccurred = true;
            }
          }

          // 실패가 발생했거나, 성공한 파일 수가 요청 파일 수와 다른 경우
          if (failureOccurred || successfulUploads.size() != requestFileCount) {
            log.error("파일 업로드 실패: 실패 개수={}", requestFileCount - successfulUploads.size());
            log.debug("업로드한 파일 롤백 시작: 업로드 된 파일 개수={}", successfulUploads.size());
            deleteFile(successfulUploads);
            throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
          }
          log.debug("SMB 파일 업로드 완료: {}개 성공", successfulUploads.size());
          return successfulUploads;
        })
        .exceptionally(throwable -> {
          log.error("파일 업로드 중 전체 오류 발생: {}", throwable.getMessage());
          throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
        });
  }

  /**
   * SMB 파일 업로드 (단일)
   */
  @Transactional
  public String uploadFile(MultipartFile file) {

    try {
      // 1. 파일 유효성 검사
      validateFile(file);

      // 2. 파일 이름 설정
      String fileName = determineFileName(file);

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

        // 6. 파일 저장

        return fileName;
      }
    } catch (Exception e) {
      log.error("SMB 파일 업로드 실패: 파일명={}", file.getOriginalFilename(), e);
      throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
    }
  }

  public void deleteFile(List<String> fileNames) {

    log.debug("SMB 파일 삭제 시작: 요청된 파일 개수={}", fileNames.size());
    if (fileNames.isEmpty()) {
      log.warn("삭제할 파일명 리스트가 비어있습니다.");
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }

    for (String fileName : fileNames) {
      try {
        Message<String> deleteMessage = MessageBuilder
            .withPayload(fileName)
            .build();
        smbDeleteChannel.send(deleteMessage);
        log.debug("파일 삭제 성공: {}", fileName);
      } catch (Exception e) {
        log.error("파일 삭제 실패: 파일명={}, 오류={}", fileName, e.getMessage());
        throw new CustomException(ErrorCode.FILE_DELETE_ERROR);
      }
    }
  }


  /**
   * 업로드된 파일 유효성 검사
   * 1. 빈 파일 검사
   * 2. MIME 타입 유효성 검사
   * 3. 이미지 파일 검사
   */
  private void validateFile(MultipartFile file) {

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
   * 파일명 설정
   * ex) 1711326597434_fileName.png
   */
  private String determineFileName(MultipartFile file) {
    String originalName = file.getOriginalFilename();
    String timeStamp = String.valueOf(System.currentTimeMillis());
    String fileName = timeStamp + "_" + originalName;
    log.debug("파일 이름 설정: {}", fileName);
    return fileName;
  }
}
