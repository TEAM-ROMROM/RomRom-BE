package com.romrom.notification.repository;

import com.romrom.common.constant.DeviceType;
import com.romrom.member.entity.Member;
import com.romrom.notification.entity.FcmToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {

  Optional<FcmToken> findByMemberAndDeviceType(Member member, DeviceType deviceType);

  List<FcmToken> findAllByMember(Member member);
}
