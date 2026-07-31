package com.romrom.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.web.RomBackApplication;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 설정값이 실제로 Spring 컨텍스트에 바인딩되는지 검증하는 통합 테스트.
 *
 * 로컬 PostgreSQL/MongoDB/Redis가 필요하다.
 * open-in-view 단언은 미보호 LAZY 연관관계 전수 감사 이후 false로 전환할 때
 * 전환 완료를 증명하는 장치다.
 *
 * @SpringBootTest는 기본적으로 management.defaults.metrics.export.enabled=false를 주입해
 * 메트릭 export(프로메테우스 포함)를 꺼버린다. prometheus 실제 스크레이핑 응답을 검증하려면
 * @AutoConfigureObservability로 다시 켜야 한다 (tracing은 이 테스트와 무관해 꺼둔다).
 */
@SpringBootTest(
    classes = RomBackApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@AutoConfigureObservability(tracing = false)
@Slf4j
class ConfigBindingTest {

  @Autowired
  Environment environment;

  @Autowired
  DataSource dataSource;

  @Autowired
  TestRestTemplate testRestTemplate;

  @Value("${admin.username}")
  String adminUsername;

  @Value("${admin.password}")
  String adminPassword;

  @Test
  @DisplayName("open-in-view가 Spring에 실제로 바인딩된다 (과거 키 위치 오류로 무시된 이력)")
  void openInView가_설정값대로_바인딩된다() {
    assertEquals("true", environment.getProperty("spring.jpa.open-in-view"));
  }

  @Test
  @DisplayName("HikariCP 풀 설정이 실제 DataSource에 반영된다")
  void 커넥션_풀_설정이_DataSource에_반영된다() {
    HikariDataSource hikariDataSource = assertInstanceOf(HikariDataSource.class, dataSource);

    assertEquals("RomRomHikariPool", hikariDataSource.getPoolName());
    assertEquals(20, hikariDataSource.getMaximumPoolSize());
    assertEquals(5, hikariDataSource.getMinimumIdle());
    assertEquals(5000, hikariDataSource.getLeakDetectionThreshold());
  }

  @Test
  @DisplayName("배치 페치 사이즈가 Hibernate에 전달된다")
  void 배치_페치_사이즈가_Hibernate에_전달된다() {
    assertEquals("100",
        environment.getProperty("spring.jpa.properties.hibernate.default_batch_fetch_size"));
  }

  @Test
  @DisplayName("health는 Dockerfile HEALTHCHECK가 쓰므로 인증 없이 열려 있어야 한다")
  void actuator_health는_인증없이_접근된다() {
    ResponseEntity<String> healthResponse =
        testRestTemplate.getForEntity("/actuator/health", String.class);

    assertEquals(HttpStatus.OK, healthResponse.getStatusCode());
  }

  @Test
  @DisplayName("노출 확대가 인증을 우회하지 않았는지 검증한다")
  void actuator_prometheus는_인증없이_접근되지_않는다() {
    ResponseEntity<String> prometheusResponse =
        testRestTemplate.getForEntity("/actuator/prometheus", String.class);

    assertTrue(
        prometheusResponse.getStatusCode() == HttpStatus.UNAUTHORIZED
            || prometheusResponse.getStatusCode() == HttpStatus.FORBIDDEN,
        "인증 없이 prometheus에 접근됐다. 실제 응답: " + prometheusResponse.getStatusCode());
  }

  @Test
  @DisplayName("관리자 토큰이면 prometheus 메트릭에 접근된다 (actuator 경로 인증 주체 미설정 회귀 방지)")
  void actuator_prometheus는_관리자_토큰으로_접근된다() {
    String adminAccessToken = loginAsAdminAndGetAccessToken();

    HttpHeaders authorizedHeaders = new HttpHeaders();
    authorizedHeaders.setBearerAuth(adminAccessToken);
    // Prometheus 스크레이핑 엔드포인트는 text/plain만 생성하므로 실제 스크레이퍼처럼 명시한다
    // (TestRestTemplate 기본 Accept는 application/json만 협상해 콘텐츠 협상 실패로 이어진다)
    authorizedHeaders.setAccept(List.of(MediaType.TEXT_PLAIN, MediaType.ALL));
    ResponseEntity<String> prometheusResponse = testRestTemplate.exchange(
        "/actuator/prometheus",
        HttpMethod.GET,
        new HttpEntity<>(authorizedHeaders),
        String.class);

    assertEquals(HttpStatus.OK, prometheusResponse.getStatusCode());
    String prometheusBody = prometheusResponse.getBody();
    assertTrue(prometheusBody != null && prometheusBody.contains("hikaricp_"),
        "hikaricp_ 지표가 응답에 없다");
    assertTrue(prometheusBody.contains("jvm_"),
        "jvm_ 지표가 응답에 없다");
  }

  /**
   * 관리자 계정으로 로그인해 accessToken을 발급받는다 (RomRomInitiation이 기동 시 계정을 자동 생성함)
   */
  private String loginAsAdminAndGetAccessToken() {
    MultiValueMap<String, String> loginForm = new LinkedMultiValueMap<>();
    loginForm.add("username", adminUsername);
    loginForm.add("password", adminPassword);

    HttpHeaders multipartHeaders = new HttpHeaders();
    multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

    ResponseEntity<String> loginResponse = testRestTemplate.postForEntity(
        "/api/admin/login",
        new HttpEntity<>(loginForm, multipartHeaders),
        String.class);

    assertEquals(HttpStatus.OK, loginResponse.getStatusCode(), "관리자 로그인 실패: " + loginResponse.getBody());

    try {
      JsonNode loginResponseJson = new ObjectMapper().readTree(loginResponse.getBody());
      return loginResponseJson.get("accessToken").asText();
    } catch (Exception e) {
      throw new IllegalStateException("관리자 로그인 응답 파싱 실패: " + loginResponse.getBody(), e);
    }
  }
}
