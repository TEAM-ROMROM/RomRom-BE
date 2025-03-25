package com.romrom.romback.domain.service;

import static com.romrom.romback.global.util.CommonUtil.nvl;

import com.romrom.romback.domain.object.dto.MemberRequest;
import com.romrom.romback.domain.object.postgres.MemberLocation;
import com.romrom.romback.domain.repository.postgres.MemberLocationRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
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
   *
   * @param request member 사용자
   *                longitude 경도
   *                latitude 위도
   *                siDo 시/도
   *                siGunGu 시/군/구
   *                eupMyoenDong 읍/면/동
   *                ri 리
   *                fullAddress 지번 주소
   *                roadAddress 도로명 주소
   */
  public void saveMemberLocation(MemberRequest request) {

    // 1. 입력 값 검증 (경도, 위도, 시/도, 시/군/구, 읍/면/동, 지번 주소, 도로명 주소)
    validateLocationRequest(request);

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
        .fullAddress(request.getFullAddress())
        .roadAddress(request.getRoadAddress())
        .build();

    // 4. 데이터베이스 저장
    memberLocationRepository.save(memberLocation);
  }

  private void validateLocationRequest(MemberRequest request) {
    checkNull(request, "요청 값이 비어있습니다.");
    checkNull(request.getLongitude(), "경도에 null 값이 요청되었습니다.");
    checkNull(request.getLatitude(), "위도에 null 값이 요청되었습니다.");
    checkEmpty(nvl(request.getSiDo(), ""), "시/도 필드에 null 값이 요청되었습니다.");
    checkEmpty(nvl(request.getSiGunGu(), ""), "시/군/구 필드에 null 값이 요청되었습니다.");
    checkEmpty(nvl(request.getEupMyoenDong(), ""), "읍/면/동 필드에 null 값이 요청되었습니다.");
    checkEmpty(nvl(request.getRi(), ""), "리 필드에 null 값이 요청되었습니다.");
    checkEmpty(nvl(request.getFullAddress(), ""), "지번 주소 필드에 null 값이 요청되었습니다.");
    checkEmpty(nvl(request.getRoadAddress(), ""), "도로명 주소 필드에 null 값이 요청되었습니다.");
  }

  private void checkNull(Object value, String message) {
    if (value == null) {
      log.error(message);
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
  }

  private void checkEmpty(String value, String message) {
    if (value.isEmpty()) {
      log.error(message);
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
  }
}
