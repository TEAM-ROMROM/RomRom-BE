package com.romrom.member.repository;

import com.romrom.member.entity.MemberKeywordAlert;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberKeywordAlertRepository extends JpaRepository<MemberKeywordAlert, UUID> {

  /**
   * 회원 키워드 목록 (최신 등록순)
   */
  List<MemberKeywordAlert> findAllByMemberMemberIdOrderByCreatedDateDesc(UUID memberId);

  long countByMemberMemberId(UUID memberId);

  boolean existsByMemberMemberIdAndNormalizedKeyword(UUID memberId, String normalizedKeyword);

  /**
   * 본인 키워드 1건 (다른 회원 키워드 > empty)
   */
  Optional<MemberKeywordAlert> findByKeywordAlertIdAndMemberMemberId(UUID keywordAlertId, UUID memberId);

  /**
   * 매칭 대상 문구에 정규화 키워드가 포함된 키워드 전체 (회원 함께 조회, 오래된 등록순)
   */
  @Query("SELECT keywordAlert FROM MemberKeywordAlert keywordAlert JOIN FETCH keywordAlert.member "
      + "WHERE LOCATE(keywordAlert.normalizedKeyword, :matchingText) > 0 "
      + "ORDER BY keywordAlert.createdDate ASC")
  List<MemberKeywordAlert> findAllContainedIn(@Param("matchingText") String matchingText);

  void deleteAllByMemberMemberId(UUID memberId);
}
