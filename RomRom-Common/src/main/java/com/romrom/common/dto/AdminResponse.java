package com.romrom.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
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
    private Page<AdminItemDto> items;

    // 회원 관련 응답 데이터
    @Schema(description = "페이지네이션된 회원 목록")
    private Page<AdminMemberDto> members;

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
    public static class AdminItemDto {
        
        @Schema(description = "물품 ID")
        private UUID itemId;

        @Schema(description = "물품명")
        private String itemName;

        @Schema(description = "물품 설명")
        private String itemDescription;

        @Schema(description = "카테고리")
        private String itemCategory;

        @Schema(description = "물품 상태")
        private String itemCondition;

        @Schema(description = "거래 상태")
        private String itemStatus;

        @Schema(description = "가격")
        private Integer price;

        @Schema(description = "좋아요 수")
        private Integer likeCount;

        @Schema(description = "메인 이미지 URL")
        private String mainImageUrl;

        @Schema(description = "판매자 닉네임")
        private String sellerNickname;

        @Schema(description = "판매자 ID")
        private UUID sellerId;

        @Schema(description = "등록일")
        private LocalDateTime createdDate;

        @Schema(description = "수정일")
        private LocalDateTime updatedDate;

        public static AdminItemDto from(Object item, Object itemImages) {
            // 이 메서드는 ItemService에서 구체적인 타입으로 구현되어야 합니다
            // 현재는 타입 안전성을 위해 기본 구현만 제공
            return AdminItemDto.builder().build();
        }
    }

    @ToString
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    public static class AdminMemberDto {
        
        @Schema(description = "회원 ID")
        private UUID memberId;

        @Schema(description = "닉네임")
        private String nickname;

        @Schema(description = "프로필 이미지 URL")
        private String profileUrl;

        @Schema(description = "이메일")
        private String email;

        @Schema(description = "활성 상태")
        private Boolean isActive;

        @Schema(description = "계정 상태")
        private String accountStatus;

        @Schema(description = "가입일")
        private LocalDateTime createdDate;

        @Schema(description = "최종 로그인일")
        private LocalDateTime lastLoginDate;
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