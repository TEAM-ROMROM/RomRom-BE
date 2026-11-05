package com.romrom.notification.repository;

import com.romrom.member.entity.Member;
import com.romrom.notification.entity.FcmToken;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {

  // 토큰 값 조회 (unique 제약 전 중복 행 대비 최근 갱신 1건)
  Optional<FcmToken> findFirstByTokenOrderByUpdatedDateDesc(String token);

  List<FcmToken> findAllByMember(Member member);

  // 로그아웃: 본인 소유 토큰 1개만 삭제
  @Modifying
  @Transactional
  @Query("DELETE FROM FcmToken f WHERE f.token = :token AND f.member.memberId = :memberId")
  int deleteByTokenAndMemberId(@Param("token") String token, @Param("memberId") UUID memberId);

  // 무효 토큰 일괄 삭제 (FCM 오류 코드 기준)
  @Modifying
  @Transactional
  @Query("DELETE FROM FcmToken f WHERE f.token IN :tokens")
  int deleteAllByTokenIn(@Param("tokens") Collection<String> tokens);

  // 오래된 토큰 정리 (갱신일 기준)
  @Modifying
  @Transactional
  @Query("DELETE FROM FcmToken f WHERE f.updatedDate < :threshold")
  int deleteAllByUpdatedDateBefore(@Param("threshold") LocalDateTime threshold);

  // 관리자: 회원 기기별 토큰 (최근 갱신순)
  @Query("SELECT f FROM FcmToken f JOIN FETCH f.member WHERE f.member.memberId = :memberId ORDER BY f.updatedDate DESC")
  List<FcmToken> findAllByMemberIdForAdmin(@Param("memberId") UUID memberId);
}
