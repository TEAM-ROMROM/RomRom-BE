package com.romrom.chat.entity.postgres;

import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.member.entity.Member;
import jakarta.persistence.*;

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

  public boolean isMember(UUID memberId) {
    return tradeReceiver.getMemberId().equals(memberId) || tradeSender.getMemberId().equals(memberId);
  }
}