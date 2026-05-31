package com.romrom.storage.constant;

import static com.romrom.storage.constant.UploadType.IMAGE;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MimeType {

  // 이미지 MIME 타입
  JPEG("image/jpeg", IMAGE),
  JPG("image/jpeg", IMAGE),
  PNG("image/png", IMAGE),
  GIF("image/gif", IMAGE),
  BMP("image/bmp", IMAGE),
  TIFF("image/tiff", IMAGE),
  SVG("image/svg+xml", IMAGE),
  WEBP("image/webp", IMAGE);


  private final String mimeType;
  private final UploadType uploadType;

  // 이미지 MIME 타입 집합 (정적 캐싱 - 호출마다 Set 재생성 방지)
  private static final Set<String> IMAGE_MIME_TYPES = Collections.unmodifiableSet(
      Arrays.stream(MimeType.values())
          .filter(type -> type.getUploadType().equals(IMAGE))
          .map(MimeType::getMimeType)
          .collect(Collectors.toSet())
  );

  private static final Map<String, String> EXTENSION_TO_MIME_TYPE = Map.of(
      "jpg", "image/jpeg",
      "jpeg", "image/jpeg",
      "png", "image/png",
      "gif", "image/gif",
      "bmp", "image/bmp",
      "tiff", "image/tiff",
      "tif", "image/tiff",
      "svg", "image/svg+xml",
      "webp", "image/webp"
  );

  // 이미지 MIME 타입 유효성 검증
  public static boolean isValidImageMimeType(String mimeType) {
    return IMAGE_MIME_TYPES.contains(mimeType.toLowerCase());
  }

  // application/octet-stream 수신 시 파일명 확장자로 MIME 타입 추론
  public static String inferMimeTypeFromFilename(String filename) {
    if (filename == null || !filename.contains(".")) {
      return null;
    }
    String fileExtension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    return EXTENSION_TO_MIME_TYPE.get(fileExtension);
  }
}
