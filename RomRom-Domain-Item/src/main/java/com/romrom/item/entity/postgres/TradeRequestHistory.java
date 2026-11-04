package com.romrom.item.entity.postgres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.ItemTradeOption;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
// TODO : 성능 고려한 복합인덱스 설정 (tradeStatus, takeItem_itemId, giveItem_itemId) -> existsTradeRequestBetweenItems 성능 개선
public class TradeRequestHistory extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID tradeRequestHistoryId; // PK

  @ManyToOne(fetch = FetchType.LAZY)
  private Item takeItem; // 요청을 받은 물품

  @ManyToOne(fetch = FetchType.LAZY)
  private Item giveItem; // 요청을 보낸 물품

  @ElementCollection
  private List<ItemTradeOption> itemTradeOptions; // 옵션 (추가금, 직거래만, 택배거래만)

  @Column(nullable = false)
  private TradeStatus tradeStatus;

  @Builder.Default
  @Column(nullable = false)
  private Boolean isNew = true;

  // 이 요청의 채팅방 ID (거래 API 응답에서 채팅 도메인 일괄 조회로 채움, 방 없음·미조회 > null, #858)
  @Transient
  private UUID chatRoomId;

  private Integer extraChargeAmount; // 추가금 (원, EXTRA_CHARGE 옵션일 때만, 구버전 요청은 null, #859)

  @Column(length = 100)
  private String requestMessage; // 한 줄 메시지 (최대 100자, 선택, #859)

  private LocalDateTime checkedAt; // 받은 사람이 상세를 처음 연 시각 (보낸 쪽 '확인함' 표시용)

  private LocalDateTime rejectedAt; // 거절 시각 (REJECTED 상태일 때만)

  // 채팅 시작 > 양쪽 물품 예약중 (해제는 ItemReservationService)
  public void startChatting() {
    this.tradeStatus = TradeStatus.CHATTING;
    this.takeItem.reserve();
    this.giveItem.reserve();
  }

  // 받은 사람 최초 확인 1회만 기록
  public void markCheckedByReceiver() {
    this.isNew = false;
    if (this.checkedAt == null) {
      this.checkedAt = LocalDateTime.now();
    }
  }

  // 거절: 행 보존 + 거절 시각 기록 (보낸 쪽 '거절됨' 표시용)
  public void reject() {
    this.tradeStatus = TradeStatus.REJECTED;
    this.rejectedAt = LocalDateTime.now();
  }

  public void requestTradeCompletion() {
    this.tradeStatus = TradeStatus.TRADE_COMPLETE_REQUESTED;
  }

  public void cancelTradeCompletionRequest() {
    this.tradeStatus = TradeStatus.CHATTING;
  }

  public void rejectTradeCompletionRequest() {
    this.tradeStatus = TradeStatus.CHATTING;
  }

  public void completeTrade() {
    this.tradeStatus = TradeStatus.TRADED;
    this.takeItem.setItemStatus(ItemStatus.EXCHANGED);
    this.giveItem.setItemStatus(ItemStatus.EXCHANGED);
    this.takeItem.releaseReservation();
    this.giveItem.releaseReservation();
  }

  // 채팅중이거나 교환 완료 요청 대기중일 때 취소로 상태 변경
  public void changeToCancelIfChatting() {
    if (this.tradeStatus == TradeStatus.CHATTING || this.tradeStatus == TradeStatus.TRADE_COMPLETE_REQUESTED) {
      this.tradeStatus = TradeStatus.CANCELED;
    }
  }
}
