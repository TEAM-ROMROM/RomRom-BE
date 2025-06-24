package com.romrom.member.repository;

import com.romrom.member.entity.MemberLocation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberLocationRepository extends JpaRepository<MemberLocation, UUID> {
  Optional<MemberLocation> findByMemberMemberId(UUID memberId);

  void deleteByMemberMemberId(UUID memberId);
}
