package com.romrom.member.service;

import static com.romrom.common.util.CommonUtil.nvl;

import com.romrom.common.util.CommonUtil;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.member.repository.MemberRepository;
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

  private final MemberRepository memberRepository;
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

    // 사용자 위치 정보 등록 완료
    Member member = request.getMember();
    member.setIsMemberLocationSaved(true);
    memberRepository.save(member);
    memberLocationRepository.save(memberLocation);
  }

}
