package com.romrom.member.repository.mongo;

import com.romrom.member.entity.mongo.SanctionHistory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SanctionHistoryRepository extends MongoRepository<SanctionHistory, String> {

  List<SanctionHistory> findByMemberIdOrderBySuspendedAtDesc(UUID memberId);

  // 해제 전 정지 이력: 종류 SUSPEND (종류 도입 전 기록은 sanctionType 없음 > 정지로 간주), 최신순
  @Query(value = "{ 'memberId': ?0, 'liftedAt': null, 'sanctionType': { $in: ['SUSPEND', null] } }",
      sort = "{ 'suspendedAt': -1 }")
  List<SanctionHistory> findActiveSuspensionsByMemberId(UUID memberId);

  /**
   * 진행 중 정지 1건 (알림 발송·물품 일괄삭제 같은 감사 기록은 제외)
   */
  default Optional<SanctionHistory> findLatestActiveSuspension(UUID memberId) {
    return findActiveSuspensionsByMemberId(memberId).stream().findFirst();
  }

  // Pageable의 Sort가 메서드명 OrderBy를 오버라이드하므로, 메서드명에 정렬 지정 불필요
  Page<SanctionHistory> findAll(Pageable pageable);

  Page<SanctionHistory> findByMemberId(UUID memberId, Pageable pageable);

  void deleteAllByMemberId(UUID memberId);
}
