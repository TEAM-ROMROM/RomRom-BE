package com.romrom.application.dto;

import com.romrom.ai.entity.mongo.AiUsageHistory;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.common.constant.TradeStatus;
import com.romrom.item.entity.mongo.LikeHistory;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.entity.postgres.TradeReview;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.mongo.LoginHistory;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.notification.entity.Announcement;
import com.romrom.notification.entity.NotificationHistory;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.entity.MemberReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.domain.Page;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class AdminResponse {

    @Schema(description = "전체 카운트")
    private Long totalCount;

    // 물품 관련 응답 데이터
    @Schema(description = "페이지네이션된 물품 목록")
    private Page<Item> items;

    @Schema(description = "물품 상세 정보 (detail 엔드포인트용)")
    private AdminItemDetailDto itemDetail;

    // 회원 관련 응답 데이터
    @Schema(description = "페이지네이션된 회원 목록")
    private Page<Member> members;

    @Schema(description = "회원 상세 정보 (detail 엔드포인트용)")
    private AdminMemberDetailDto memberDetail;

    @Schema(description = "단일 회원 정보 (status 엔드포인트 응답용)")
    private Member member;

    // 채팅방 관련 응답 데이터
    @Schema(description = "soft-delete된(청소 대기) 채팅방 목록")
    private Page<ChatRoom> deletedChatRooms;

    @Schema(description = "채팅방 상세 - 채팅방 엔티티")
    private ChatRoom chatRoom;

    @Schema(description = "채팅방 상세 - 메시지 목록")
    private List<ChatMessage> chatMessages;

    // 기타 통계 데이터
    @Schema(description = "대시보드 통계 데이터")
    private AdminDashboardStats dashboardStats;

    // 동접자(온라인 사용자) 집계 데이터
    @Schema(description = "앱 전체 동접자 수 (최근 5분 내 인증 API 호출한 고유 회원 수)")
    private Long onlineMemberCount;

    @Schema(description = "채팅 온라인 회원 수 (현재 채팅방 접속 중인 고유 회원 수)")
    private Long chatOnlineMemberCount;

    @Schema(description = "JWT 액세스 토큰 (로그인 응답)")
    private String accessToken;

    @Schema(description = "JWT 리프레시 토큰 (로그인 응답)")
    private String refreshToken;

    @Schema(description = "관리자 계정명 (로그인 응답)")
    private String username;

    @Schema(description = "관리자 권한 (로그인 응답)")
    private String role;

    // 제재 이력 관련 응답 데이터
    @Schema(description = "제재 이력 목록")
    private Page<SanctionHistory> sanctionHistories;

    // 신고 관련 응답 데이터
    @Schema(description = "물품 신고 목록")
    private List<ItemReport> itemReports;

    @Schema(description = "회원 신고 목록")
    private List<MemberReport> memberReports;

    @Schema(description = "물품 신고 상세")
    private ItemReport itemReport;

    @Schema(description = "회원 신고 상세")
    private MemberReport memberReport;

    @Schema(description = "신고 상태별 통계")
    private Map<String, Map<String, Long>> reportStats;

    @Schema(description = "신고 상세 원스톱 처리용 요약 (#709: 피신고자/대상 요약 + 동일 피신고자 누적 신고 건수)")
    private ReportResolveDetail reportResolveDetail;

    // 거래 관련 응답 데이터
    @Schema(description = "페이지네이션된 거래 이력 목록")
    private Page<TradeRequestHistory> trades;

    @Schema(description = "거래 상세 정보 (detail 엔드포인트용)")
    private AdminTradeDetailDto tradeDetail;

    @Schema(description = "최근 교환완료(TRADED) 거래 목록 (대시보드 recent-trades 용)")
    private List<TradeRequestHistory> recentTrades;

    // 후기 관련 응답 데이터
    @Schema(description = "페이지네이션된 후기 목록 (관리자 후기 관리용, blindInfo 포함)")
    private Page<TradeReview> reviews;

    // 공지사항 관련 응답 데이터
    @Schema(description = "공지사항 목록")
    private List<Announcement> announcements;

    // 알림 설정 관련 응답 데이터
    @Schema(description = "관리자 알림 수신 이메일")
    private String alertEmail;

    @Schema(description = "신고 알림 쓰로틀링 (분)")
    private Integer alertThrottleMinutes;

    @Schema(description = "SMTP 호스트")
    private String mailSmtpHost;

    @Schema(description = "SMTP 포트")
    private Integer mailSmtpPort;

    @Schema(description = "SMTP 발송 계정")
    private String mailSmtpUsername;

    // AI 설정 관련 응답 데이터
    @Schema(description = "AI 기본 프로바이더")
    private String aiPrimaryProvider;

    @Schema(description = "AI 폴백 프로바이더")
    private String aiFallbackProvider;

    @Schema(description = "Ollama 활성화 여부")
    private String aiOllamaEnabled;

    @Schema(description = "Ollama Base URL")
    private String aiOllamaBaseUrl;

    @Schema(description = "Ollama Chat 모델")
    private String aiOllamaChatModel;

    @Schema(description = "Ollama Embedding 모델")
    private String aiOllamaEmbeddingModel;

    @Schema(description = "Vertex AI 활성화 여부")
    private String aiVertexEnabled;

    @Schema(description = "Vertex AI Generation 모델")
    private String aiVertexGenerationModel;

    @Schema(description = "Vertex AI Embedding 모델")
    private String aiVertexEmbeddingModel;

    @Schema(description = "Vertex AI Generation 위치")
    private String aiVertexGenerationLocation;

    @Schema(description = "Vertex AI Embedding 위치")
    private String aiVertexEmbeddingLocation;

    // 앱 버전 설정 관련 응답 데이터
    @Schema(description = "앱 최신 버전")
    private String appLatestVersion;

    @Schema(description = "앱 최소 필수 버전")
    private String appMinVersion;

    @Schema(description = "Android Google Play URL")
    private String appStoreAndroid;

    @Schema(description = "iOS App Store URL")
    private String appStoreIos;

    // UGC 필터 관련 응답 데이터
    @Schema(description = "UGC 필터 정규식 패턴 목록 (JSON 배열 문자열)")
    private String ugcFilterPatterns;

    // 서버 점검 모드 관련 필드
    @Schema(description = "점검 모드 활성화 여부")
    private String maintenanceEnabled;

    @Schema(description = "점검 안내 메시지")
    private String maintenanceMessage;

    @Schema(description = "점검 예상 종료 시간 (ISO 8601)")
    private String maintenanceEndTime;

    // 이미지 압축/업로드 설정 관련 응답 데이터
    @Schema(description = "압축 스킵 대상 contentType")
    private String imageCompressSkipContentType;

    @Schema(description = "압축 스킵 최대 용량(byte)")
    private String imageCompressSkipMaxSizeBytes;

    @Schema(description = "이미지 업로드 병렬 스레드풀 크기")
    private String imageUploadParallelPoolSize;

    // AI 가격 예측 프롬프트 관련 응답 데이터
    @Schema(description = "가격 예측 AI System Prompt 본문 ({{INPUT_TEXT}} 치환 템플릿)")
    private String aiPromptPricePredictionInstruction;

    // AI 채팅 추천 프롬프트 관련 응답 데이터
    @Schema(description = "채팅 추천 AI System Prompt 본문")
    private String aiPromptChatRecommendationInstruction;

    @Schema(description = "채팅 추천 AI 활성화 여부 (\"true\"/\"false\")")
    private String aiPromptChatRecommendationEnabled;

    // ============ 관리자 회원 360 View 관련 응답 데이터 ============

    @Schema(description = "회원 360 카드 응답")
    private AdminMemberDetail360Dto memberDetail360;

    @Schema(description = "회원 보유 물품 sub-list 페이지")
    private org.springframework.data.domain.Page<Item> memberItemsPage;

    @Schema(description = "회원 거래 sub-list 페이지")
    private org.springframework.data.domain.Page<TradeRequestHistory> memberTradesPage;

    @Schema(description = "회원 채팅방 sub-list 페이지")
    private org.springframework.data.domain.Page<ChatRoom> memberChatRoomsPage;

    @Schema(description = "회원이 신고 당한 물품 신고 페이지")
    private org.springframework.data.domain.Page<ItemReport> memberItemReportsReceivedPage;

    @Schema(description = "회원이 신고 당한 회원 신고 페이지")
    private org.springframework.data.domain.Page<MemberReport> memberMemberReportsReceivedPage;

    @Schema(description = "회원이 신고한 물품 신고 페이지")
    private org.springframework.data.domain.Page<ItemReport> memberItemReportsFiledPage;

    @Schema(description = "회원이 신고한 회원 신고 페이지")
    private org.springframework.data.domain.Page<MemberReport> memberMemberReportsFiledPage;

    @Schema(description = "회원 제재 이력 페이지 (sub-list)")
    private org.springframework.data.domain.Page<SanctionHistory> memberSanctionsPage;

    @Schema(description = "회원 로그인 이력 페이지")
    private org.springframework.data.domain.Page<LoginHistory> memberLoginHistoryPage;

    @Schema(description = "회원 좋아요 페이지")
    private org.springframework.data.domain.Page<LikeHistory> memberLikesPage;

    @Schema(description = "회원 AI 사용 페이지")
    private org.springframework.data.domain.Page<AiUsageHistory> memberAiUsagePage;

    @Schema(description = "회원 알림 이력 페이지")
    private org.springframework.data.domain.Page<NotificationHistory> memberNotificationHistoryPage;

    @Schema(description = "일괄 작업 결과 (성공/실패 개별 결과)")
    private List<BulkActionResult> bulkActionResults;

    // 공통 페이징 응답 데이터
    @Schema(description = "전체 페이지 수")
    private Integer totalPages;

    @Schema(description = "전체 요소 수")
    private Long totalElements;

    @Schema(description = "현재 페이지")
    private Integer currentPage;

    // 로그 관리 관련 응답 데이터
    @Schema(description = "로그 라인 목록 (조회/검색/ gz 조회 결과)")
    private List<String> logLines;

    @Schema(description = "에러 집계 목록")
    private List<AdminLogErrorSummary> logErrorSummaries;

    @Schema(description = "로그 파일 목록")
    private List<AdminLogFileInfo> logFiles;

    @Schema(description = "로그 총 용량 (bytes)")
    private Long logTotalSizeBytes;

    @Schema(description = "로그 파일 개수")
    private Integer logFileCount;

    @Schema(description = "디스크 여유 공간 (bytes, 조회 가능 시)")
    private Long diskFreeBytes;

    @Schema(description = "디스크 전체 용량 (bytes, 조회 가능 시)")
    private Long diskTotalBytes;

    @ToString
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @Schema(description = "관리자 회원 상세 DTO")
    public static class AdminMemberDetailDto {

        @Schema(description = "회원 기본 정보")
        private Member member;

        @Schema(description = "회원 등록 물품 전체 목록 (FOR_SALE/RESERVED/SOLD_OUT 포함)")
        private List<Item> items;

        @Schema(description = "해당 회원이 신고당한 내역")
        private List<MemberReport> memberReports;

        @Schema(description = "신고 건수")
        private Long reportCount;
    }

    @ToString
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @Schema(description = "관리자 물품 상세 DTO")
    public static class AdminItemDetailDto {

        @Schema(description = "물품 기본 정보 (이미지 포함, isAdminHidden 포함)")
        private Item item;

        @Schema(description = "관리자 노출 차단 사유 (Item.adminHideReason 은 @JsonIgnore 라 admin 응답 전용으로 별도 노출, 내부용)")
        private String adminHideReason;

        @Schema(description = "해당 물품에 대한 신고 이력")
        private List<ItemReport> itemReports;

        @Schema(description = "해당 물품이 포함된 거래 이력")
        private List<TradeRequestHistory> tradeHistories;
    }

    @ToString
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @Schema(description = "관리자 거래 상세 DTO")
    public static class AdminTradeDetailDto {

        @Schema(description = "거래 이력 (takeItem/giveItem 및 각 소유 회원 정보 포함)")
        private TradeRequestHistory tradeRequestHistory;

        @Schema(description = "연결된 채팅방 (CHATTING 이상 상태에서만 존재, PENDING이면 null)")
        private ChatRoom chatRoom;

        @Schema(description = "거래 채팅 전체 내역 (시간순 오름차순, 채팅방 없으면 빈 리스트). 관리자 분쟁/신고 추적용")
        private List<ChatMessage> chatMessages;
    }

    @ToString
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    public static class AdminDashboardStats {

        @Schema(description = "전체 회원 수")
        private Long totalMembers;

        @Schema(description = "활성 회원 수")
        private Long activeMembers;

        @Schema(description = "전체 물품 수")
        private Long totalItems;

        @Schema(description = "판매중 물품 수")
        private Long activeItems;

        @Schema(description = "오늘 가입 회원 수")
        private Long todayNewMembers;

        @Schema(description = "오늘 등록 물품 수")
        private Long todayNewItems;

        @Schema(description = "진행중 거래 건수 (PENDING + CHATTING + TRADE_COMPLETE_REQUESTED)")
        private Long ongoingTrades;

        @Schema(description = "신고접수 건수 (물품+회원 PENDING 합산)")
        private Long pendingReports;

        @Schema(description = "거래 상태별 건수 (데이터 주도 카드 렌더용, 모든 TradeStatus 키 포함). 기간 필터 적용 시 해당 기간 집계")
        private Map<TradeStatus, Long> tradeStatusCounts;

        @Schema(description = "신규 후기 건수 (기간 필터 적용 시 해당 기간 작성 후기 수)")
        private Long newReviewCount;
    }

    @ToString
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @Schema(description = "로그 파일 정보")
    public static class AdminLogFileInfo {
        @Schema(description = "파일명 (예: romrom.log, romrom.log.2026-06-07.0.gz)")
        private String fileName;

        @Schema(description = "파일 크기 (bytes)")
        private Long fileSizeBytes;

        @Schema(description = "마지막 수정 시각")
        private java.time.LocalDateTime lastModifiedAt;
    }

    @ToString
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @Schema(description = "에러 집계 요약")
    public static class AdminLogErrorSummary {
        @Schema(description = "예외 클래스명 또는 에러 식별 키")
        private String exceptionClassName;

        @Schema(description = "발생 횟수")
        private Integer occurrenceCount;

        @Schema(description = "마지막 발생 시각")
        private java.time.LocalDateTime lastOccurredAt;

        @Schema(description = "대표 메시지")
        private String representativeMessage;
    }

    @ToString
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @Schema(description = "신고 원스톱 처리용 신고 상세 요약 (#709)")
    public static class ReportResolveDetail {
        @Schema(description = "신고 유형 (ITEM / MEMBER)")
        private String reportType;

        @Schema(description = "피신고자 memberId (정지 대상)")
        private UUID reportedMemberId;

        @Schema(description = "피신고자 닉네임")
        private String reportedMemberNickname;

        @Schema(description = "피신고자 계정 상태")
        private String reportedMemberAccountStatus;

        @Schema(description = "피신고자가 현재 정지 상태인지")
        private Boolean reportedMemberSuspended;

        @Schema(description = "신고 대상 물품 ID (물품 신고 시)")
        private UUID reportedItemId;

        @Schema(description = "신고 대상 물품명 (물품 신고 시)")
        private String reportedItemName;

        @Schema(description = "신고 대상 물품 상태 (물품 신고 시)")
        private String reportedItemStatus;

        @Schema(description = "동일 피신고자에 대한 누적 신고 건수 (물품+회원 신고 합산)")
        private Long reportedMemberTotalReportCount;
    }
}
