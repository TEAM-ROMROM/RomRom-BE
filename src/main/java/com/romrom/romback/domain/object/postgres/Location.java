package com.romrom.romback.domain.object.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.geolatte.geom.Point;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
public class Location {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID locationId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member member;

  // 경도와 위도를 PostGIS Point 타입으로 저장
  @Column(columnDefinition = "geography(Point, 4326)", nullable = false)
  private Point geom;

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

  // 지번 주소
  private String fullAddress;

  // 도로명 주소
  private String roadAddress;
}
