package com.romrom.application.dto;

import com.romrom.ai.entity.mongo.AiUsageHistory;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.item.entity.mongo.LikeHistory;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.entity.mongo.LoginHistory;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.notification.entity.NotificationHistory;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.entity.MemberReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "관리자 회원 360 카드 응답 (12 카드)")
public class AdminMemberDetail360Dto {

  @Schema(description = "1. 기본 정보 카드")
  private BasicInfoCard basicInfoCard;

  @Schema(description = "2. 보유 물품 카드")
  private OwnedItemsCard ownedItemsCard;

  @Schema(description = "3. 거래 내역 카드")
  private TradesCard tradesCard;

  @Schema(description = "4. 채팅방 카드")
  private ChatRoomsCard chatRoomsCard;

  @Schema(description = "5. 받은 신고 카드")
  private ReportsReceivedCard reportsReceivedCard;

  @Schema(description = "6. 신고한 내역 카드")
  private ReportsFiledCard reportsFiledCard;

  @Schema(description = "7. 제재 이력 카드")
  private SanctionsCard sanctionsCard;

  @Schema(description = "8. 알림 동의 카드")
  private NotificationConsentCard notificationConsentCard;

  @Schema(description = "9. 로그인 이력 카드")
  private LoginHistoryCard loginHistoryCard;

  @Schema(description = "10. 앱 사용성 카드")
  private AppUsageCard appUsageCard;

  @Schema(description = "11. 좋아요 카드")
  private LikesCard likesCard;

  @Schema(description = "12. AI 사용 카드")
  private AiUsageCard aiUsageCard;

  @Schema(description = "13. 거래 통계 카드")
  private TradeStatsCard tradeStatsCard;

  // ============================ Inner Card DTOs ============================

  @ToString
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class BasicInfoCard {
    private Member member;
    private MemberLocation memberLocation;
    private SanctionHistory activeSanction;
  }

  @ToString
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class OwnedItemsCard {
    private long totalCount;
    private long forSaleCount;
    private long reservedCount;
    private long soldOutCount;
    private long deletedCount;
    private List<Item> recentItems;
  }

  @ToString
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class TradesCard {
    private long totalCount;
    private long requestedByMemberCount;
    private long receivedByMemberCount;
    private long pendingCount;
    private long chattingCount;
    private long tradedCount;
    private long canceledCount;
    private List<TradeRequestHistory> recentTrades;
  }

  @ToString
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ChatRoomsCard {
    private long totalCount;
    private long activeCount;
    private List<ChatRoom> recentChatRooms;
  }

  @ToString
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ReportsReceivedCard {
    private long totalCount;
    private long itemReportCount;
    private long memberReportCount;
    private List<ItemReport> recentItemReports;
    private List<MemberReport> recentMemberReports;
  }

  @ToString
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ReportsFiledCard {
    private long totalCount;
    private long itemReportCount;
    private long memberReportCount;
    private List<ItemReport> recentItemReportsFiled;
    private List<MemberReport> recentMemberReportsFiled;
  }

  @ToString
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SanctionsCard {
    private long totalCount;
    private Boolean isCurrentlyActive;
    private SanctionHistory activeSanction;
    private List<SanctionHistory> recentSanctions;
  }

  @ToString
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class NotificationConsentCard {
    private Boolean isMarketingInfoAgreed;
    private Boolean isActivityNotificationAgreed;
    private Boolean isChatNotificationAgreed;
    private Boolean isContentNotificationAgreed;
    private Boolean isTradeNotificationAgreed;
    private long totalCount;
    private List<NotificationHistory> recentNotifications;
  }

  @ToString
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class LoginHistoryCard {
    private long totalCount;
    private long successCount;
    private long failCount;
    private LocalDateTime lastLoginAt;
    private String lastIpAddress;
    private List<LoginHistory> recentLogins;
  }

  @ToString
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class AppUsageCard {
    private LocalDateTime lastActiveAt;
    private long daysSinceJoin;
  }

  @ToString
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class LikesCard {
    private long totalCount;
    private List<LikeHistory> recentLikes;
  }

  @ToString
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class AiUsageCard {
    private long totalCount;
    private long pricePredictionCount;
    private long ugcFilterCount;
    private long imageAnalysisCount;
    private long embeddingCount;
    private long categoryMatchingCount;
    private List<AiUsageHistory> recentUsages;
  }

  @ToString
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class TradeStatsCard {
    private double completionRate;
    private double cancellationRate;
    private Double avgTradeDays;
    private long longestPendingDays;
  }
}
