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

    @Schema(description = "검색 키워드 (물품명, 설명, 판매자 닉네임)")
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
    private String title;

    @Schema(description = "공지사항 내용 (생성 시 사용)")
    private String content;

}
