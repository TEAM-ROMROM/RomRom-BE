package com.romrom.application.dto;

import com.romrom.item.entity.postgres.Item;
import com.romrom.member.entity.Member;
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

    private String accessToken;

    private String refreshToken;

    private String username;

    private String role;

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
    }
}
