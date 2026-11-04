package com.romrom.notification.repository;

import com.romrom.member.entity.Member;
import com.romrom.notification.entity.NotificationHistory;
import com.romrom.notification.event.NotificationType;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, UUID> {

  // 알림 목록 조회
  Page<NotificationHistory> findByMemberOrderByPublishedAtDesc(Member member, Pageable pageable);

  // isRead 기준 필터링 알림 개수 조회
  long countByMemberAndIsRead(Member member, Boolean isRead);

  // 알림 목록 조회 (특정 타입 제외, 옛 앱용 #861)
  Page<NotificationHistory> findByMemberAndNotificationTypeNotInOrderByPublishedAtDesc(
      Member member, Collection<NotificationType> excludedTypes, Pageable pageable);

  // isRead 기준 알림 개수 (특정 타입 제외, 옛 앱용 #861)
  long countByMemberAndIsReadAndNotificationTypeNotIn(
      Member member, Boolean isRead, Collection<NotificationType> excludedTypes);

  // 사용자 모든 알림 읽음 처리
  @Modifying
  @Query("""
    UPDATE NotificationHistory n
    SET n.isRead = true
    WHERE n.member.memberId = :memberId
    AND n.isRead = false
    """)
  int markAllAsReadByMemberId(@Param("memberId") UUID memberId);

  // 사용자의 전체 알림 삭제
  void deleteAllByMember(Member member);

  // === Admin 360 View 전용 ===
  Page<NotificationHistory> findByMember_MemberIdOrderByPublishedAtDesc(UUID memberId, Pageable pageable);

  long countByMember_MemberId(UUID memberId);
}
