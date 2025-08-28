package com.romrom.common.exception;

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

  INVALID_REQUIRED_TERMS_AGREED(HttpStatus.BAD_REQUEST, "필수 이용약관에 동의하지 않았습니다."),

  // MEMBER LOCATION

  MEMBER_LOCATION_NOT_FOUND(HttpStatus.NOT_FOUND, "회원 위치 정보가 등록되지 않았습니다."),

  // FILE

  INVALID_FILE_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 파일이 요청되었습니다."),

  FILE_UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드 중 오류가 발생했습니다."),

  FILE_DELETE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "파일 삭제 중 오류가 발생했습니다."),

  // REPORT

  DUPLICATE_REPORT(HttpStatus.FORBIDDEN, "같은 물품을 여러 번 신고할 수 없습니다."),

  TOO_LONG_EXTRA_COMMENT(HttpStatus.BAD_REQUEST, "기타 의견을 글자 수 제한을 넘겨서 작성할 수 없습니다."),

  NULL_EXTRA_COMMENT(HttpStatus.BAD_REQUEST, "기타 의견을 빈 값으로 요청할 수 없습니다."),

  // ITEM

  ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 물품을 찾을 수 없습니다."),

  INVALID_ITEM_OWNER(HttpStatus.FORBIDDEN, "작성자 본인만 아이템을 수정할 수 있습니다."),

  ITEM_VALUE_PREDICTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "아이템 가격 예측에 실패하였습니다."),

  // ITEM CUSTOM TAG

  TOO_MANY_CUSTOM_TAGS(HttpStatus.BAD_REQUEST, "커스텀 태그의 최대 개수를 초과하였습니다."),

  TOO_LONG_CUSTOM_TAGS(HttpStatus.BAD_REQUEST, "커스텀 태그의 최대 길이를 초과하였습니다."),

  // TRADE

  ALREADY_REQUESTED_ITEM(HttpStatus.BAD_REQUEST, "이미 요청을 보낸 물품입니다."),

  TRADE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "거래 요청이 존재하지 않습니다."),

  // CHAT

  CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),

  NOT_CHATROOM_MEMBER(HttpStatus.FORBIDDEN, "채팅방의 멤버만 접근할 수 있는 권한입니다."),

  CANNOT_CREATE_SELF_CHATROOM(HttpStatus.BAD_REQUEST, "자기 자신과는 채팅방을 생성할 수 없습니다."),

  INVALID_SENDER(HttpStatus.FORBIDDEN, "보낸이 정보가 올바르지 않습니다."),

  TRADE_REQUEST_NOT_ACCEPTED(HttpStatus.FORBIDDEN, "거래 요청이 승인 상태가 아닙니다."),

  NOT_TRADE_REQUEST_MEMBER(HttpStatus.FORBIDDEN, "거래 요청의 당사자만 채팅방을 생성할 수 있습니다."),

  // ITEM LIKES

  SELF_LIKE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "내 아이템에는 좋아요를 누를 수 없습니다."),

  TRADE_CANCEL_FORBIDDEN(HttpStatus.FORBIDDEN, "거래 요청을 취소할 수 있는 권한이 없습니다."),

  // Vertex AI Client

  VERTEX_REQUEST_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Vertex AI 요청 JSON 직렬화에 실패했습니다."),

  VERTEX_API_CALL_FAILED(HttpStatus.BAD_GATEWAY, "Vertex AI HTTP 응답에 실패했습니다."),

  VERTEX_RESPONSE_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Vertex AI 응답 파싱을 실패하였습니다."),

  VERTEX_AUTH_TOKEN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Vertex AI Token을 받아오지 못했습니다."),

  VERTEX_PREDICTIONS_MALFORMED(HttpStatus.BAD_GATEWAY, "Vertex AI 응답의 predictions 형식이 잘못되었습니다."),

  VERTEX_PREDICTIONS_MISSING(HttpStatus.BAD_REQUEST, "Vertex AI 응답에서 predictions 누락 또는 잘못된 형식입니다."),

  // EMBEDDING

  EMBEDDING_NOT_FOUND(HttpStatus.NOT_FOUND, "임베딩을 찾을 수 없습니다.");

  private final HttpStatus status;
  private final String message;
}
