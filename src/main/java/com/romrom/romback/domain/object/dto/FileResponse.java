package com.romrom.romback.domain.object.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class FileResponse {

  private String originalFileName; // 원본 파일 명

  private String uploadedFileName; // 업로드 파일 명

  private String filePath; // 파일 경로

  private Long fileSize; // 파일 크기
}
