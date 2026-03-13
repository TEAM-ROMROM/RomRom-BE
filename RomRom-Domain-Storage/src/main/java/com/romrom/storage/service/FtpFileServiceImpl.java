package com.romrom.storage.service;

import com.romrom.storage.util.FileUtil;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FtpFileServiceImpl implements FileService {

  private final MessageChannel ftpUploadChannel;
  private final MessageChannel ftpDeleteChannel;

  @Value("${file.dir}")
  private String dir;

  @Value("${file.domain}")
  private String domain;

  @Override
  public String uploadFile(String fileName, InputStream data, long size, String contentType) {
    String filePath = FileUtil.combineBaseAndPath(dir, fileName);

    log.debug("FTP 업로드 시작: 파일명={}, 크기={} 바이트", fileName, size);

    Message<InputStream> message = MessageBuilder
        .withPayload(data)
        .setHeader("file_name", fileName)
        .build();

    ftpUploadChannel.send(message);
    log.debug("FTP 업로드 성공: {}", fileName);

    return filePath;
  }

  @Override
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

  @Override
  public String buildImageUrl(String filePath) {
    return FileUtil.combineBaseAndPath(domain, filePath);
  }

  @Override
  public String extractFilePath(String imageUrl) {
    String base = FileUtil.removeTrailingSlash(domain);
    if (imageUrl.startsWith(base)) {
      return imageUrl.length() <= base.length() + 1 ? null : imageUrl.substring(base.length() + 1);
    }
    return null;
  }
}
