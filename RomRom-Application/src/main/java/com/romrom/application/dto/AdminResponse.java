package com.romrom.application.dto;

import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.notification.entity.Announcement;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.entity.MemberReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
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

    // 기타 통계 데이터
    @Schema(description = "대시보드 통계 데이터")
    private AdminDashboardStats dashboardStats;

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

    // 거래 관련 응답 데이터
    @Schema(description = "페이지네이션된 거래 이력 목록")
    private Page<TradeRequestHistory> trades;

    @Schema(description = "거래 상세 정보 (detail 엔드포인트용)")
    private AdminTradeDetailDto tradeDetail;

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

    // 공통 페이징 응답 데이터
    @Schema(description = "전체 페이지 수")
    private Integer totalPages;

    @Schema(description = "전체 요소 수")
    private Long totalElements;

    @Schema(description = "현재 페이지")
    private Integer currentPage;

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
    }
}
