package com.romrom.chat.entity.postgres;

import com.romrom.common.entity.postgres.BasePostgresEntity;
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
  @Column(name = "chat_room_id", length = 80, nullable = false, updatable = false)
  private UUID chatRoomId;

  @Column(nullable = false)
  private UUID memberA;

  @Column(nullable = false)
  private UUID memberB;

  public boolean isMember(UUID memberId) {
    return memberA.equals(memberId) || memberB.equals(memberId);
  }

  // DB 저장 전, memberA가 항상 작은 값이 되도록 보정
  // 엔티티가 처음 영속(persist) 될 때 @PrePersist 메서드가 실행
  // 즉, 한 번의 쿼리로 조회가 가능
  @PrePersist
  private void ensureOrder() {
    if (memberA.compareTo(memberB) > 0) {
      UUID tmp = memberA; memberA = memberB; memberB = tmp;
    }
  }
}