package com.romrom.member.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.geolatte.geom.G2D;
import org.geolatte.geom.Point;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
public class MemberLocation extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID memberLocationId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member member;

  // 경도와 위도를 PostGIS Point 타입으로 저장
  @Column(columnDefinition = "geography(Point, 4326)", nullable = false)
  @JsonIgnore
  private Point<G2D> geom;

  // 시/도 (서울특별시, 경기도)
  @Column(nullable = false)
  private String siDo;

  // 시/군/구 (강남구, 구리시)
  @Column(nullable = false)
  private String siGunGu;

  // 읍/면/동 (대치1동, 교문1동)
  @Column(nullable = false)
  private String eupMyoenDong;

  // 리
  private String ri;

  /** 값 정규화: 앞뒤 공백 제거, null→null 유지 */
  @PrePersist @PreUpdate
  private void normalize() {
    this.siDo       = trimOrNull(this.siDo);
    this.siGunGu    = trimOrNull(this.siGunGu);
    this.eupMyoenDong = trimOrNull(this.eupMyoenDong);
    this.ri         = trimOrNull(this.ri);
  }

  private static String trimOrNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  /** 도메인 규칙: 비어있는 필드는 제외하고 공백으로 합침 */
  public String fullLocation() {
    return java.util.stream.Stream.of(siDo, siGunGu, eupMyoenDong, ri)
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(java.util.stream.Collectors.joining(" "));
  }
}
