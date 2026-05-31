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

  // FE 라우팅 트리거: 알림 클릭 시 게시글 삭제 안내 화면으로 진입 (RomRom-FE#786)
  // FE deep_link_router는 "romrom://" 제거 후 routeKey="item/deleted"로 매칭하므로 슬래시 경로를 유지한다
  private static final String ITEM_DELETED_DEEP_LINK = "romrom://item/deleted";

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

    // FE가 안내 화면에 게시글 제목/삭제 사유를 구조화 표시할 수 있도록 data 필드로 분리 전달 (#741)
    // 키 이름(itemName, deleteReason)은 FE deep_link_router가 읽는 extraData 키와 정확히 일치시켜야 한다
    // adminDeleteDetail(상세 사유)은 "사용자 비공개" 정책이므로 payload에 포함하지 않는다 — 카테고리 description만 노출
    this.payload.put("deepLink", ITEM_DELETED_DEEP_LINK);
    this.payload.put("itemName", deletedItemName);
    this.payload.put("deleteReason", resolveReasonDescription());
  }

  public String getTitle() {
    return NotificationType.ITEM_DELETED_BY_ADMIN.getTitle();
  }

  public String getBody() {
    return String.format("회원님의 물품 '%s'이(가) '%s' 사유로 삭제되었습니다.", deletedItemName, resolveReasonDescription());
  }

  // 삭제 사유 카테고리 description 추출 (사유 미지정 시 '기타'로 폴백) — getBody/payload 공용
  private String resolveReasonDescription() {
    return adminDeleteReason != null
        ? adminDeleteReason.getDescription()
        : ItemAdminDeleteReason.ETC.getDescription();
  }
}
