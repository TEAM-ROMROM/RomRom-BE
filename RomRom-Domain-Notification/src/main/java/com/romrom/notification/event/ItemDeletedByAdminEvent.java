package com.romrom.notification.event;

import com.romrom.common.constant.ItemAdminDeleteReason;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;

/**
 * 관리자에 의해 물품 삭제 알림 이벤트 (다수 회원 대상)
 * - 기존 NotificationEvent는 단일 targetMemberId 설계이므로 독립 클래스로 생성
 * - Body: "{물품명}이(가) {삭제사유} 사유로 삭제되었습니다."
 */
@Getter
public class ItemDeletedByAdminEvent {

  private final List<UUID> affectedMemberIds;
  private final String deletedItemName;
  private final ItemAdminDeleteReason adminDeleteReason;
  private final Map<String, String> payload;

  public ItemDeletedByAdminEvent(List<UUID> affectedMemberIds, String deletedItemName, ItemAdminDeleteReason adminDeleteReason) {
    this.affectedMemberIds = affectedMemberIds;
    this.deletedItemName = deletedItemName;
    this.adminDeleteReason = adminDeleteReason;
    this.payload = new HashMap<>();
    this.payload.put("notificationType", NotificationType.ITEM_DELETED_BY_ADMIN.name());
  }

  public String getTitle() {
    return NotificationType.ITEM_DELETED_BY_ADMIN.getTitle();
  }

  public String getBody() {
    String reasonDescription = adminDeleteReason != null
        ? adminDeleteReason.getDescription()
        : ItemAdminDeleteReason.ETC.getDescription();
    return String.format("회원님의 물품 '%s'이(가) '%s' 사유로 삭제되었습니다.", deletedItemName, reasonDescription);
  }
}
