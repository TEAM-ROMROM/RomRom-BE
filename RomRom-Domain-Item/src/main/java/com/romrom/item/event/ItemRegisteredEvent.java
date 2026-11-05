package com.romrom.item.event;

import com.romrom.item.entity.postgres.Item;
import java.util.UUID;

/**
 * 물품 등록 완료 (#873): 등록 트랜잭션 커밋 후 키워드 알림 매칭에 사용 (위치 없으면 좌표 null)
 */
public record ItemRegisteredEvent(
    UUID itemId,
    UUID ownerMemberId,
    String itemName,
    String itemDescription,
    Double longitude,
    Double latitude
) {

  /**
   * 저장된 물품 > 이벤트 (지연 로딩 없이 필요한 값만 복사)
   */
  public static ItemRegisteredEvent from(Item savedItem) {
    return new ItemRegisteredEvent(
        savedItem.getItemId(),
        savedItem.getMember().getMemberId(),
        savedItem.getItemName(),
        savedItem.getItemDescription(),
        savedItem.getLongitude(),
        savedItem.getLatitude()
    );
  }
}
