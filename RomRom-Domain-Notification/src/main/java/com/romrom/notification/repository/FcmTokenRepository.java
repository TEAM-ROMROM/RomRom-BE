package com.romrom.notification.repository;

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
}
