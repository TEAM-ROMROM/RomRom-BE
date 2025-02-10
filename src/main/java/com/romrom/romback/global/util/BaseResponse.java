package com.romrom.romback.global.util;

import com.romrom.romback.global.exception.ErrorDetail;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@ToString
public class BaseResponse<T> {

  private boolean success;
  private T data;
  private ErrorDetail errorDetail;

  /**
   * 요청이 성공했을 때
   */
  public static <T> BaseResponse<T> success(T data) {
    return BaseResponse.<T>builder()
        .success(true)
        .data(data)
        .errorDetail(null)
        .build();
  }

  /**
   * 요청이 실패했을 때
   */
  public static <T> BaseResponse<T> error(String errorCode, String errorMessage) {
    return BaseResponse.<T>builder()
        .success(false)
        .data(null)
        .errorDetail(ErrorDetail.builder()
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .build())
        .build();
  }

  /**
   * 요청 실패 + Validation 상세 오류 전달
   */
  public static <T> BaseResponse<T> errorWithValidation(
      String errorCode,
      String errorMessage,
      Map<String, String> validation) {

    return BaseResponse.<T>builder()
        .success(false)
        .data(null)
        .errorDetail(ErrorDetail.builder()
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .validation(validation)
            .build())
        .build();
  }
}
