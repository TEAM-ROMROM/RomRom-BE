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
