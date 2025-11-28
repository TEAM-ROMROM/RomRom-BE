package com.romrom.member.repository;

import com.romrom.member.entity.MemberLocation;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberLocationRepository extends JpaRepository<MemberLocation, UUID> {
  Optional<MemberLocation> findByMemberMemberId(UUID memberId);

  void deleteByMemberMemberId(UUID memberId);

  /**
   * N+1 방지를 위해, Member ID 목록으로 MemberLocation 목록을 한 번에 조회합니다.
   * MemberLocation이 Member를 FetchType.LAZY로 가지고 있으므로,
   * Member ID를 직접 조회하도록 @Query를 사용하거나 메서드 이름을 사용합니다.
   */
  @Query("SELECT ml FROM MemberLocation ml WHERE ml.member.memberId IN :memberIds")
  List<MemberLocation> findByMemberMemberIdIn(Set<UUID> memberIds);
}
