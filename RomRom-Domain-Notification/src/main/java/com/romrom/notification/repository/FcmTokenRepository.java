package com.romrom.notification.repository;

import org.springframework.data.repository.query.Param;
import com.romrom.common.constant.DeviceType;
import com.romrom.member.entity.Member;
import com.romrom.notification.entity.FcmToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {

  Optional<FcmToken> findByMemberAndDeviceType(Member member, DeviceType deviceType);

  List<FcmToken> findAllByMember(Member member);

  @Query("SELECT f FROM FcmToken f WHERE f.member.isDeleted = false")
  List<FcmToken> findAllByActiveMember();

  void deleteByToken(String token);

  // 관리자: 회원 기기별 토큰 (최근 갱신순)
  @Query("SELECT f FROM FcmToken f JOIN FETCH f.member WHERE f.member.memberId = :memberId ORDER BY f.updatedDate DESC")
  List<FcmToken> findAllByMemberIdForAdmin(@Param("memberId") UUID memberId);
}
