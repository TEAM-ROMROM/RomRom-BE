package com.romrom.common.service;

import static com.hierynomus.msdtyp.AccessMask.GENERIC_WRITE;
import static com.hierynomus.mssmb2.SMB2CreateDisposition.FILE_CREATE;
import static com.hierynomus.mssmb2.SMB2ShareAccess.ALL;

import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.util.FileUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmbService implements FileService {

  private final Session smbSession;

  @Value("${file.root-dir}")
  private String rootDir;

  @Value("${file.dir}")
  private String dir;

  /**
   * SMB 파일 업로드 (단일)
   *
   * @return 업로드 된 파일 Path
   */
  @Transactional
  public String uploadFile(MultipartFile file) {

    try {
      // 파일 유효성 검사
      FileUtil.validateFile(file);

      // 파일 이름 설정
      String originalFilename = FileUtil.validateAndExtractFilename(file);
      String fileName = FileUtil.generateFilename(originalFilename);
      String filePath = FileUtil.generateFilePath(dir, fileName);

      try (DiskShare share = (DiskShare) smbSession.connectShare(rootDir);
          InputStream is = file.getInputStream()) {

        // 3) 파일 생성 & 쓰기
        try (OutputStream os = share.openFile(
            filePath,
            EnumSet.of(GENERIC_WRITE),
            null,
            ALL,
            FILE_CREATE,
            null
        ).getOutputStream()) {
          is.transferTo(os);
        }
        log.debug("SMBJ 파일 업로드 성공: {}", filePath);
        return filePath;
      } catch (IOException e) {
        log.error("SMBJ 파일 업로드 실패: {}", fileName, e);
        throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR);
      }
    } catch (Exception e) {
      log.error("SMBJ 연결 실패", e);
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * SMB 파일 삭제
   *
   * @param
   */
  @Transactional
  public void deleteFile(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      throw new CustomException(ErrorCode.INVALID_FILE_REQUEST);
    }
    try (DiskShare share = (DiskShare) smbSession.connectShare(rootDir)) {
      share.rm(fileName);
      log.debug("SMBJ 파일 삭제 성공: {}", fileName);
    } catch (IOException e) {
      log.error("SMBJ 파일 삭제 실패: {}", fileName, e);
      throw new CustomException(ErrorCode.FILE_DELETE_ERROR);
    }
  }
}
