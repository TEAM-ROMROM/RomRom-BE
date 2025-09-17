package com.romrom.web.service;

import static com.romrom.common.dto.deprecated.LogUtil.lineLog;

import com.romrom.common.constant.MimeType;
import com.romrom.common.service.SmbService;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Slf4j
class SmbServiceTest {

  @Autowired
  SmbService smbService;

  private List<MultipartFile> createMockMultipartFile(int count) {
    List<MultipartFile> files = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      String fileName = "테스트파일" + i;
      files.add(createMockMultipartFile(fileName, MimeType.PNG));
    }
    return files;
  }

  private MockMultipartFile createMockMultipartFile(String fileName, MimeType mimeType) {
    log.debug("MockMultipartFile 생성 시작");
    try {
      byte[] content = fileName.getBytes();
      String contentType = mimeType.getMimeType();
      MockMultipartFile mockMultipartFile = new MockMultipartFile("multipartFile", fileName, contentType, content);
      log.debug("MockMultipartFile 생성 성공: 파일명={}", mockMultipartFile.getOriginalFilename());
      return mockMultipartFile;
    } catch (Exception e) {
      log.error("MockMultipartFile 생성 중 오류 발생: {}", e.getMessage());
      throw new RuntimeException("MockMultipartFile 생성 실패", e);
    }
  }

  @Test
  void 단일_파일_업로드_성공() {
    lineLog("SMB 단일 파일 업로드 시작");
    MockMultipartFile file = createMockMultipartFile("테스트파일", MimeType.PNG);
    smbService.uploadFile(file);
    lineLog("SMB 단일 파일 업로드 성공");
  }
}