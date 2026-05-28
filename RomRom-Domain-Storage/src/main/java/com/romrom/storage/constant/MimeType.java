package com.romrom.storage.constant;

import static com.romrom.storage.constant.UploadType.IMAGE;

import java.util.Arrays;
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

  private static final Set<String> MIME_TYPES = Arrays
      .stream(MimeType.values())
      .map(MimeType::getMimeType)
      .collect(Collectors.toSet());

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

  // 유효한 MimeType 인지 검증
  public static boolean isValidMimeType(String mimeType) {
    return MIME_TYPES.contains(mimeType.toLowerCase());
  }

  // 특정 UploadType에 해당하는 MimeType 집합
  public static Set<String> getMimeTypesByUploadType(UploadType uploadType) {
    return Arrays.stream(MimeType.values())
        .filter(type -> type.getUploadType().equals(uploadType))
        .map(MimeType::getMimeType)
        .collect(Collectors.toSet());
  }

  // 각 UploadType별 유효성 검증 메서드
  public static boolean isValidImageMimeType(String mimeType) {
    return getMimeTypesByUploadType(IMAGE).contains(mimeType.toLowerCase());
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
