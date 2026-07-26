package com.romrom.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * application.yml의 설정 키 위치·존재 여부를 검증한다.
 *
 * Spring 컨텍스트를 띄우지 않으므로 DB·Redis 없이 어디서나 실행된다.
 * 과거 open-in-view 키가 spring.jpa.properties.hibernate 아래에 있어
 * 조용히 무시된 이력이 있어, 키 위치 자체를 회귀 대상으로 삼는다.
 */
class ApplicationYamlStructureTest {

  private Map<String, Object> applicationYamlRoot;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void loadApplicationYaml() throws Exception {
    try (InputStream applicationYamlStream =
        getClass().getResourceAsStream("/application.yml")) {
      assertNotNull(applicationYamlStream, "application.yml을 클래스패스에서 찾지 못했다");
      applicationYamlRoot = new Yaml().loadAs(applicationYamlStream, Map.class);
    }
  }

  @Test
  @DisplayName("open-in-view는 spring.jpa 직속에 있어야 Spring Boot가 인식한다")
  void openInView가_spring_jpa_직속에_존재한다() {
    assertNotNull(valueAt("spring", "jpa", "open-in-view"));
  }

  @Test
  @DisplayName("open-in-view가 hibernate properties 아래에 있으면 무시된다")
  void openInView가_hibernate_properties_아래에_없다() {
    assertNull(valueAt("spring", "jpa", "properties", "hibernate", "open-in-view"));
  }

  @Test
  @DisplayName("show_sql은 stdout 직행이라 운영에서 차단 불가 — 제거되어야 한다")
  void showSql_관련_설정이_모두_제거되었다() {
    assertNull(valueAt("spring", "jpa", "properties", "hibernate", "show_sql"));
    assertNull(valueAt("spring", "jpa", "properties", "hibernate", "format_sql"));
    assertNull(valueAt("spring", "jpa", "properties", "hibernate", "use_sql_comments"));
  }

  @Test
  @DisplayName("배치 페치 사이즈가 N+1을 IN 절로 접는 핵심 설정")
  void 배치_페치_사이즈가_설정되어_있다() {
    assertEquals(100, valueAt("spring", "jpa", "properties", "hibernate", "default_batch_fetch_size"));
    assertEquals(50, valueAt("spring", "jpa", "properties", "hibernate", "jdbc", "batch_size"));
    assertEquals(100, valueAt("spring", "jpa", "properties", "hibernate", "jdbc", "fetch_size"));
  }

  @Test
  @DisplayName("ddl-auto와 generate-ddl은 의도된 운영 설계 — 변경 금지")
  void 스키마_생성_설정은_기존_값을_유지한다() {
    assertEquals("update", valueAt("spring", "jpa", "hibernate", "ddl-auto"));
    assertEquals(true, valueAt("spring", "jpa", "generate-ddl"));
  }

  @Test
  @DisplayName("커넥션 풀 설정이 없으면 HikariCP 기본값 10으로 동작한다")
  void 커넥션_풀_설정이_존재한다() {
    assertEquals("RomRomHikariPool", valueAt("spring", "datasource", "hikari", "pool-name"));
    assertEquals(20, valueAt("spring", "datasource", "hikari", "maximum-pool-size"));
    assertEquals(5, valueAt("spring", "datasource", "hikari", "minimum-idle"));
  }

  @Test
  @DisplayName("누수 탐지는 D의 OSIV 전환에 필요한 커넥션 보유 시간 근거를 수집한다")
  void 커넥션_누수_탐지가_켜져_있다() {
    assertEquals(5000, valueAt("spring", "datasource", "hikari", "leak-detection-threshold"));
  }

  @Test
  @DisplayName("메트릭 노출이 없으면 B/C/D의 개선 효과를 측정할 수단이 없다")
  void actuator에_prometheus가_노출된다() {
    Object exposureInclude = valueAt("management", "endpoints", "web", "exposure", "include");
    assertNotNull(exposureInclude);
    String exposureIncludeText = exposureInclude.toString();
    org.junit.jupiter.api.Assertions.assertTrue(
        exposureIncludeText.contains("prometheus"),
        "prometheus가 노출 목록에 없다: " + exposureIncludeText);
    org.junit.jupiter.api.Assertions.assertTrue(
        exposureIncludeText.contains("health"),
        "health가 빠지면 Dockerfile HEALTHCHECK와 Traefik 블루/그린 전환이 깨진다: " + exposureIncludeText);
  }

  /**
   * 중첩 맵을 키 경로로 탐색한다. 경로가 중간에 끊기면 null을 반환해
   * "키가 없다"와 "값이 null이다"를 동일하게 취급한다 — 설정 검증 목적상 둘은 같다.
   */
  @SuppressWarnings("unchecked")
  private Object valueAt(String... yamlKeyPath) {
    Object currentNode = applicationYamlRoot;
    for (String yamlKey : yamlKeyPath) {
      if (!(currentNode instanceof Map)) {
        return null;
      }
      currentNode = ((Map<String, Object>) currentNode).get(yamlKey);
    }
    return currentNode;
  }
}
