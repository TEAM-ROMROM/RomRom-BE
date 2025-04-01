package com.romrom.romback.domain.service;

import static com.romrom.romback.global.util.CommonUtil.nvl;

import com.romrom.romback.domain.object.dto.MemberRequest;
import com.romrom.romback.domain.object.postgres.MemberLocation;
import com.romrom.romback.domain.repository.postgres.MemberLocationRepository;
import com.romrom.romback.global.util.CommonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geolatte.geom.G2D;
import org.geolatte.geom.Point;
import org.geolatte.geom.crs.CoordinateReferenceSystems;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberLocationService {

  private final MemberLocationRepository memberLocationRepository;

  /**
   * 사용자 위치 정보 저장
   */
  public void saveMemberLocation(MemberRequest request) {

    // 1. 입력 값 검증 (경도, 위도, 시/도, 시/군/구, 읍/면/동)
    CommonUtil.checkNotNullOrEmpty(request, "요청 값이 비어있습니다.");
    CommonUtil.checkNotNullOrEmpty(request.getLongitude(), "경도에 null 값이 요청되었습니다.");
    CommonUtil.checkNotNullOrEmpty(request.getLatitude(), "위도에 null 값이 요청되었습니다.");
    CommonUtil.checkNotNullOrEmpty(nvl(request.getSiDo(), ""), "시/도 필드에 null 값이 요청되었습니다.");
    CommonUtil.checkNotNullOrEmpty(nvl(request.getSiGunGu(), ""), "시/군/구 필드에 null 값이 요청되었습니다.");
    CommonUtil.checkNotNullOrEmpty(nvl(request.getEupMyoenDong(), ""), "읍/면/동 필드에 null 값이 요청되었습니다.");

    // 2. PostGIS Point 객체 생성 (경도, 위도 -> EPSG:4326)
    Point<G2D> geom = new Point<>(new G2D(request.getLongitude(), request.getLatitude()), CoordinateReferenceSystems.WGS84);
    log.debug("EPSG:4326 Point 객체 생성완료: {}", geom);

    // 3. 사용자 위치 정보 저장
    MemberLocation memberLocation = MemberLocation.builder()
        .member(request.getMember())
        .geom(geom)
        .siDo(request.getSiDo())
        .siGunGu(request.getSiGunGu())
        .eupMyoenDong(request.getEupMyoenDong())
        .ri(request.getRi())
        .build();

    memberLocationRepository.save(memberLocation);
  }

}
