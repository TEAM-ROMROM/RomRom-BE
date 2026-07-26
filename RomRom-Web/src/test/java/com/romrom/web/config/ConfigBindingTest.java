package com.romrom.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.romrom.web.RomBackApplication;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * 설정값이 실제로 Spring 컨텍스트에 바인딩되는지 검증하는 통합 테스트.
 *
 * 로컬 PostgreSQL/MongoDB/Redis가 필요하다.
 * open-in-view 단언은 미보호 LAZY 연관관계 전수 감사 이후 false로 전환할 때
 * 전환 완료를 증명하는 장치다.
 */
@SpringBootTest(
    classes = RomBackApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Slf4j
class ConfigBindingTest {

  @Autowired
  Environment environment;

  @Autowired
  DataSource dataSource;

  @Autowired
  TestRestTemplate testRestTemplate;

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
}
