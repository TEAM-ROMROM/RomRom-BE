package com.romrom.chat.entity.postgres;

import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.member.entity.Member;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID chatRoomId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(nullable = false)
  private Member tradeReceiver;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(nullable = false)
  private Member tradeSender;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(unique = true, nullable = false)
  private TradeRequestHistory tradeRequestHistory;

  // soft delete 시각. null이면 활성 방, non-null이면 배치 청소 대기 상태 (#750)
  private LocalDateTime deletedAt;

  // soft delete 표시 (멱등 — 이미 표시된 방이면 무시하여 중복 삭제 요청 방어)
  public void softDelete() {
    if (this.deletedAt == null) {
      this.deletedAt = LocalDateTime.now();
    }
  }

  public boolean isMember(UUID memberId) {
    return tradeReceiver.getMemberId().equals(memberId) || tradeSender.getMemberId().equals(memberId);
  }
}