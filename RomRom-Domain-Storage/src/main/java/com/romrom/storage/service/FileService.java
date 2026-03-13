package com.romrom.storage.service;

import java.io.InputStream;

public interface FileService {

  /**
   * 파일 업로드 (순수 전송만 담당, 압축은 StorageService에서 처리)
   *
   * @param fileName    저장할 파일명
   * @param data        파일 데이터 스트림
   * @param size        파일 크기 (바이트)
   * @param contentType MIME 타입
   * @return 저장된 파일 경로 (각 구현체의 형식에 따름)
   */
  String uploadFile(String fileName, InputStream data, long size, String contentType);

  /**
   * 파일 삭제
   *
   * @param filePath 파일 경로
   */
  void deleteFile(String filePath);

  /**
   * 파일 경로로 외부 접근 가능한 이미지 URL 생성
   *
   * @param filePath 저장된 파일 경로
   * @return 이미지 URL
   */
  String buildImageUrl(String filePath);

  /**
   * 이미지 URL에서 파일 경로 추출
   *
   * @param imageUrl 이미지 URL
   * @return 파일 경로 (deleteFile에 전달할 수 있는 형태)
   */
  String extractFilePath(String imageUrl);
}
