package com.romrom.application.dto;

import com.romrom.item.entity.postgres.Item;
import com.romrom.member.entity.Member;
import com.romrom.report.entity.MemberReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
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
