package com.romrom.auth.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.jwt.JwtUtil;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.service.OnlinePresenceService;
import com.romrom.member.entity.Member;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * 인증 필터 에러 응답 검증 (#814): 블랙리스트 토큰 401, 정지 회원 suspendedUntil ISO 문자열
 */
class TokenAuthenticationFilterErrorResponseTest {

  private static final String ACCESS_TOKEN = "access-token";

  // JacksonConfig와 같은 설정 (ISO 날짜)
  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private final JwtUtil jwtUtil = mock(JwtUtil.class);
  private final TokenAuthenticationFilter tokenAuthenticationFilter =
      new TokenAuthenticationFilter(jwtUtil, mock(OnlinePresenceService.class), objectMapper);

  @Test
  @DisplayName("블랙리스트 토큰은 필터 밖으로 던지지 않고 401 TOKEN_BLACKLISTED")
  void 블랙리스트_토큰은_401이다() throws Exception {
    when(jwtUtil.validateToken(ACCESS_TOKEN)).thenThrow(new CustomException(ErrorCode.TOKEN_BLACKLISTED));
    MockHttpServletResponse response = new MockHttpServletResponse();

    tokenAuthenticationFilter.doFilter(apiRequestWithToken(), response, new MockFilterChain());

    assertEquals(401, response.getStatus());
    assertEquals(ErrorCode.TOKEN_BLACKLISTED.name(), readJson(response).get("errorCode").asText());
  }

  @Test
  @DisplayName("정지 회원 응답의 suspendedUntil은 ISO 문자열 (배열 아님)")
  void 정지_회원_응답은_ISO_날짜다() throws Exception {
    Member suspendedMember = Member.builder()
        .memberId(UUID.randomUUID())
        .accountStatus(AccountStatus.SUSPENDED_ACCOUNT)
        .suspendReason("reason")
        .suspendedUntil(LocalDateTime.of(2026, 11, 30, 12, 0))
        .build();
    CustomUserDetails suspendedUserDetails = new CustomUserDetails(suspendedMember);
    when(jwtUtil.validateToken(ACCESS_TOKEN)).thenReturn(true);
    when(jwtUtil.getAuthentication(ACCESS_TOKEN))
        .thenReturn(new UsernamePasswordAuthenticationToken(suspendedUserDetails, null, List.of()));
    MockHttpServletResponse response = new MockHttpServletResponse();

    tokenAuthenticationFilter.doFilter(apiRequestWithToken(), response, new MockFilterChain());

    assertEquals(403, response.getStatus());
    JsonNode suspendedResponseJson = readJson(response);
    assertTrue(suspendedResponseJson.get("suspendedUntil").isTextual());
    assertEquals("2026-11-30T12:00:00", suspendedResponseJson.get("suspendedUntil").asText());
  }

  private MockHttpServletRequest apiRequestWithToken() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/item/list");
    request.addHeader("Authorization", "Bearer " + ACCESS_TOKEN);
    return request;
  }

  private JsonNode readJson(MockHttpServletResponse response) throws Exception {
    return objectMapper.readTree(response.getContentAsString());
  }
}
