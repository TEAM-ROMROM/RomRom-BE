package com.romrom.application.dto;

import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemStatus;
import com.romrom.report.enums.ReportStatus;
import com.romrom.report.enums.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
@NoArgsConstructor
public class AdminRequest {

    private String username;

    @ToString.Exclude
    private String password;

    // 공통 페이지네이션 필드
    @Schema(description = "페이지 번호", defaultValue = "0")
    @Builder.Default
    private Integer pageNumber = 0;

    @Schema(description = "페이지 크기", defaultValue = "20")
    @Builder.Default
    private Integer pageSize = 20;

    @Schema(description = "정렬 필드", defaultValue = "createdDate")
    @Builder.Default
    private String sortBy = "createdDate";

    @Schema(description = "정렬 방향", defaultValue = "DESC")
    @Builder.Default
    private Sort.Direction sortDirection = Sort.Direction.DESC;

    // 물품 관련 필드
    @Schema(description = "물품 ID (삭제, 상세 조회 시 사용)")
    private UUID itemId;

    @Schema(description = "검색 키워드 (물품명, 설명, 판매자 닉네임, 회원 닉네임)")
    private String searchKeyword;

    @Schema(description = "물품 카테고리 필터")
    private ItemCategory itemCategory;

    @Schema(description = "물품 상태 필터")
    private ItemCondition itemCondition;

    @Schema(description = "거래 상태 필터")
    private ItemStatus itemStatus;

    @Schema(description = "최소 가격")
    private Integer minPrice;

    @Schema(description = "최대 가격")
    private Integer maxPrice;

    @Schema(description = "등록일 시작일 (yyyy-MM-dd)")
    private String startDate;

    @Schema(description = "등록일 종료일 (yyyy-MM-dd)")
    private String endDate;

    // 회원 관련 필드
    @Schema(description = "회원 ID (삭제, 상세 조회 시 사용)")
    private UUID memberId;

    @Schema(description = "변경할 계정 상태 (status 엔드포인트용)")
    private AccountStatus accountStatus;

    // 신고 관련 필드
    @Schema(description = "신고 ID (상세 조회, 상태 변경 시 사용)")
    private UUID reportId;

    @Schema(description = "신고 유형 (ITEM / MEMBER, 상태 변경 시 사용)")
    private ReportType reportType;

    @Schema(description = "변경할 신고 상태 (상태 변경 시 사용)")
    private ReportStatus newReportStatus;

    @Schema(description = "신고 상태 필터 (목록 조회 시 사용)")
    private ReportStatus reportStatus;

    // 공지사항 관련 필드
    @Schema(description = "공지사항 ID (삭제 시 사용)")
    private UUID announcementId;

    @Schema(description = "공지사항 제목 (생성 시 사용)")
    private String announcementTitle;

    @Schema(description = "공지사항 내용 (생성 시 사용)")
    private String announcementContent;

    // 알림 설정 관련 필드
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

    @Schema(description = "SMTP 발송 비밀번호")
    @ToString.Exclude
    private String mailSmtpPassword;

    // AI 설정 관련 필드
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

    // 앱 버전 설정 관련 필드 (app.latest.version은 CI/CD에서 자동 갱신되므로 AdminRequest에 포함하지 않음)
    @Schema(description = "앱 최소 필수 버전")
    private String appMinVersion;

    @Schema(description = "Android Google Play URL")
    private String appStoreAndroid;

    @Schema(description = "iOS App Store URL")
    private String appStoreIos;

    // UGC 필터 관련 필드
    @Schema(description = "UGC 필터 정규식 패턴 목록 (JSON 배열 문자열, 예: [\"씨발\",\"fuck\"])")
    private String ugcFilterPatterns;

}
