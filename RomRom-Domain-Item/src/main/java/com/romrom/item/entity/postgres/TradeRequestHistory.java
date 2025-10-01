package com.romrom.item.entity.postgres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
}
