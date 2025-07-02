package com.romrom.common.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {

  String uploadFile(MultipartFile file);

  void deleteFile(String filePath);
}
