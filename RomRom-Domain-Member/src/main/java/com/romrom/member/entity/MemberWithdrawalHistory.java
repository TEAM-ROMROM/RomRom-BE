package com.romrom.member.entity;

import com.romrom.common.constant.MemberWithdrawalReason;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 회원 탈퇴 기록: 사유(선택), 상세 사유, 탈퇴 시 취소한 진행 중 거래 수 (#862)
 * 회원 행은 탈퇴 후에도 남지만 연관 정리 순서와 무관하도록 FK 없이 회원 ID만 보관
 */
@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "member_withdrawal_history")
public class MemberWithdrawalHistory extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID memberWithdrawalHistoryId;

  @Column(nullable = false, updatable = false)
  private UUID memberId;

  // null: 구버전 앱 탈퇴 (사유 미수집)
  @Enumerated(EnumType.STRING)
  private MemberWithdrawalReason withdrawalReason;

  @Column(length = 500)
  private String withdrawalReasonDetail;

  // 탈퇴 시 CANCELED 처리한 진행 중 거래 수 (대기·채팅 중·교환 완료 요청)
  @Column(nullable = false)
  private Integer canceledTradeRequestCount;
}
