package com.romrom.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 압축된 이미지 정보를 담는 DTO
 */
@ToString(exclude = "data")
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class CompressedImage {

  private byte[] data;

  private String fileName;

  private String contentType;

  private long originalSize;

  private long compressedSize;
}
