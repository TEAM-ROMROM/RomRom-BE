package com.romrom.romback.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

  // GLOBAL

  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 발생했습니다."),

  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),

  ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근이 거부되었습니다."),

  // AUTH

  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다."),

  MISSING_AUTH_TOKEN(HttpStatus.UNAUTHORIZED, "인증 토큰이 필요합니다."),

  INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 엑세스 토큰입니다."),

  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),

  EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "엑세스 토큰이 만료되었습니다."),

  EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다."),

  REFRESH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "리프레시 토큰을 찾을 수 없습니다."),

  TOKEN_BLACKLISTED(HttpStatus.UNAUTHORIZED, "블랙리스트처리된 토큰이 요청되었습나다."),

  // OAUTH

  EMPTY_SOCIAL_AUTH_TOKEN(HttpStatus.BAD_REQUEST, "소셜 로그인 인증 토큰이 제공되지 않았습니다."),

  INVALID_SOCIAL_PLATFORM(HttpStatus.BAD_REQUEST, "유효하지 않은 소셜 플랫폼입니다."),

  SOCIAL_API_ERROR(HttpStatus.BAD_GATEWAY, "소셜 로그인 API 호출에 실패하였습니다."),

  INVALID_SOCIAL_MEMBER_INFO(HttpStatus.BAD_REQUEST, "소셜 로그인 회원 정보가 올바르지 않습니다."),

  // MEMBER

  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원를 찾을 수 없습니다."),

  EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 가입된 이메일입니다."),

  // MEMBER LOCATION

  MEMBER_LOCATION_NOT_FOUND(HttpStatus.NOT_FOUND, "회원 위치 정보가 등록되지 않았습니다."),

  // FILE

  INVALID_FILE_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 파일이 요청되었습니다."),

  FILE_UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SMB 파일 업로드 중 오류가 발생했습니다."),

  FILE_DELETE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SMB 파일 삭제 중 오류가 발생했습니다."),

  // ITEM

  ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 물품을 찾을 수 없습니다."),

  // ITEM CUSTOM TAG

  TOO_MANY_CUSTOM_TAGS(HttpStatus.BAD_REQUEST, "커스텀 태그의 최대 개수를 초과하였습니다."),

  TOO_LONG_CUSTOM_TAGS(HttpStatus.BAD_REQUEST, "커스텀 태그의 최대 길이를 초과하였습니다."),

  // TRADE

  ALREADY_REQUESTED_ITEM(HttpStatus.BAD_REQUEST, "이미 요청을 보낸 물품입니다."),

  TRADE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "거래 요청이 존재하지 않습니다."),

  TRADE_CANCEL_FORBIDDEN(HttpStatus.FORBIDDEN, "거래 요청을 취소할 수 있는 권한이 없습니다.");

  private final HttpStatus status;
  private final String message;
}
