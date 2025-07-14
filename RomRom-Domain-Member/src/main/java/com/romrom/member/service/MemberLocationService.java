package com.romrom.member.service;

import com.romrom.member.dto.MemberRequest;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.member.repository.MemberRepository;
import java.util.Optional;
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

    Member member = request.getMember();

    // PostGIS Point 객체 생성 (경도, 위도 -> EPSG:4326)
    Point<G2D> geom = new Point<>(new G2D(request.getLongitude(), request.getLatitude()), CoordinateReferenceSystems.WGS84);
    log.debug("EPSG:4326 Point 객체 생성완료: {}", geom);

    Optional<MemberLocation> optionalLocation = memberLocationRepository.findByMemberMemberId(member.getMemberId());

    if (optionalLocation.isPresent()) {
      log.debug("사용자 위치 정보를 업데이트합니다");
      updateMemberLocation(optionalLocation.get(), request, geom);
    } else {
      MemberLocation memberLocation = createMemberLocationEntity(request, geom);
      member.setIsMemberLocationSaved(true);
      memberRepository.save(member);
      memberLocationRepository.save(memberLocation);
    }
  }

  /**
   * MemberLocation 객체 생성
   */
  private MemberLocation createMemberLocationEntity(MemberRequest request, Point<G2D> geom) {
    return MemberLocation.builder()
        .member(request.getMember())
        .geom(geom)
        .siDo(request.getSiDo())
        .siGunGu(request.getSiGunGu())
        .eupMyoenDong(request.getEupMyoenDong())
        .ri(request.getRi())
        .build();
  }

  /**
   * MemberLocation 업데이트
   */
  private void updateMemberLocation(MemberLocation memberLocation, MemberRequest request, Point<G2D> geom) {
    memberLocation.setGeom(geom);
    memberLocation.setSiDo(request.getSiDo());
    memberLocation.setSiGunGu(request.getSiGunGu());
    memberLocation.setEupMyoenDong(request.getEupMyoenDong());
    memberLocation.setRi(request.getRi());
    memberLocationRepository.save(memberLocation);
  }

}
