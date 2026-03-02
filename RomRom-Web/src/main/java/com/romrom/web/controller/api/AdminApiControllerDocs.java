package com.romrom.web.controller.api;

import com.romrom.common.dto.AdminRequest;
import com.romrom.common.dto.AdminResponse;
import com.romrom.common.dto.Author;
import com.romrom.report.dto.AdminReportRequest;
import com.romrom.report.dto.AdminReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Admin API", description = "관리자 전용 API")
public interface AdminApiControllerDocs {

  // ==================== Auth ====================

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.27", author = Author.SUHSAECHAN, issueNumber = 0, description = "관리자 로그인 API 구현"),
  })
  @Operation(
      summary = "관리자 로그인",
      description = """
      ## 인증(JWT): **불필요**

      ## 요청 파라미터 (AdminRequest)
      - **`username`**: 관리자 아이디
      - **`password`**: 관리자 비밀번호

      ## 반환값 (AdminResponse)
      - **`accessToken`**: 발급된 AccessToken
      - **`refreshToken`**: 발급된 RefreshToken
      - **`username`**: 관리자 아이디
      - **`role`**: 관리자 권한

      ## 에러코드
      - **`INVALID_CREDENTIALS`**: 아이디 또는 비밀번호가 올바르지 않습니다.
      """
  )
  ResponseEntity<AdminResponse> login(AdminRequest request, HttpServletResponse response);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.27", author = Author.SUHSAECHAN, issueNumber = 0, description = "관리자 로그아웃 API 구현"),
  })
  @Operation(
      summary = "관리자 로그아웃",
      description = """
      ## 인증(JWT): **필요**

      ## 동작 설명
      - refreshToken 쿠키 무효화 처리
      - accessToken, refreshToken, authStatus 쿠키 삭제

      ## 반환값
      - 성공 시 상태코드 200 (OK)와 빈 응답 본문
      """
  )
  ResponseEntity<Void> logout(String refreshTokenFromCookie, HttpServletResponse response);

  // ==================== Dashboard ====================

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.27", author = Author.SUHSAECHAN, issueNumber = 0, description = "관리자 대시보드 통계 API 구현"),
  })
  @Operation(
      summary = "대시보드 통계 조회",
      description = """
      ## 인증(JWT): **필요** (관리자)

      ## 반환값 (AdminResponse)
      - **`dashboardStats.totalMembers`**: 전체 회원 수
      - **`dashboardStats.totalItems`**: 전체 물품 수
      """
  )
  ResponseEntity<AdminResponse> getDashboardStats();

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.27", author = Author.SUHSAECHAN, issueNumber = 0, description = "대시보드 최근 가입 회원 목록 API 구현"),
  })
  @Operation(
      summary = "대시보드 최근 가입 회원 목록 조회",
      description = """
      ## 인증(JWT): **필요** (관리자)

      ## 반환값 (AdminResponse)
      - **`members`**: 최근 가입 회원 8명 목록 (Page)
        - **`memberId`**: 회원 ID (UUID)
        - **`nickname`**: 닉네임
        - **`profileUrl`**: 프로필 이미지 URL
        - **`email`**: 이메일
        - **`isActive`**: 활성 상태
        - **`createdDate`**: 가입일
        - **`lastLoginDate`**: 최종 로그인일
      - **`totalCount`**: 반환된 회원 수
      """
  )
  ResponseEntity<AdminResponse> getRecentMembers();

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.27", author = Author.SUHSAECHAN, issueNumber = 0, description = "대시보드 최근 등록 물품 목록 API 구현"),
  })
  @Operation(
      summary = "대시보드 최근 등록 물품 목록 조회",
      description = """
      ## 인증(JWT): **필요** (관리자)

      ## 반환값 (AdminResponse)
      - **`items`**: 최근 등록 물품 8개 목록 (Page)
        - **`itemId`**: 물품 ID (UUID)
        - **`itemName`**: 물품명
        - **`itemDescription`**: 물품 설명
        - **`itemCategory`**: 카테고리
        - **`itemCondition`**: 물품 상태
        - **`itemStatus`**: 거래 상태
        - **`price`**: 가격
        - **`likeCount`**: 좋아요 수
        - **`mainImageUrl`**: 대표 이미지 URL
        - **`sellerNickname`**: 판매자 닉네임
        - **`sellerId`**: 판매자 ID (UUID)
        - **`createdDate`**: 등록일
      - **`totalCount`**: 반환된 물품 수
      """
  )
  ResponseEntity<AdminResponse> getRecentItems();

  // ==================== Items ====================

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.27", author = Author.SUHSAECHAN, issueNumber = 552, description = "관리자용 물품 단건 조회 API 추가"),
  })
  @Operation(
      summary = "관리자용 물품 단건 조회",
      description = """
      ## 인증(JWT): **필요** (관리자)

      ## 요청 파라미터 (AdminRequest)
      - **`itemId`**: 조회할 물품 ID (UUID, 필수)

      ## 반환값 (AdminResponse)
      - **`item`**: 물품 Entity 전체 정보
        - **`itemId`**: 물품 ID
        - **`itemName`**: 물품명
        - **`itemDescription`**: 물품 설명
        - **`itemCategory`**: 카테고리
        - **`itemCondition`**: 물품 상태
        - **`itemStatus`**: 거래 상태
        - **`price`**: 가격
        - **`likeCount`**: 좋아요 수
        - **`itemImages`**: 이미지 목록
        - **`member`**: 판매자 정보
        - **`createdDate`**: 등록일

      ## 에러코드
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      """
  )
  ResponseEntity<AdminResponse> getItemDetail(AdminRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.27", author = Author.SUHSAECHAN, issueNumber = 0, description = "관리자용 물품 목록 조회 API 구현"),
  })
  @Operation(
      summary = "관리자용 물품 목록 조회",
      description = """
      ## 인증(JWT): **필요** (관리자)

      ## 요청 파라미터 (AdminRequest)
      - **`pageNumber`**: 페이지 번호 (기본값: 0)
      - **`pageSize`**: 페이지 크기 (기본값: 20)
      - **`sortBy`**: 정렬 필드 (기본값: createdDate)
      - **`sortDirection`**: 정렬 방향 (기본값: DESC)
      - **`searchKeyword`**: 검색 키워드 (물품명, 설명, 판매자 닉네임)
      - **`itemCategory`**: 카테고리 필터
      - **`itemCondition`**: 물품 상태 필터
      - **`itemStatus`**: 거래 상태 필터
      - **`minPrice`**: 최소 가격
      - **`maxPrice`**: 최대 가격
      - **`startDate`**: 등록일 시작 (yyyy-MM-dd)
      - **`endDate`**: 등록일 종료 (yyyy-MM-dd)

      ## 반환값 (AdminResponse)
      - **`items`**: 물품 목록 (Page<AdminItemDto>)
      - **`totalCount`**: 전체 물품 수
      """
  )
  ResponseEntity<AdminResponse> getItems(AdminRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.27", author = Author.SUHSAECHAN, issueNumber = 0, description = "관리자용 물품 삭제 API 구현"),
  })
  @Operation(
      summary = "관리자용 물품 삭제",
      description = """
      ## 인증(JWT): **필요** (관리자)

      ## Path Variable
      - **`itemId`**: 삭제할 물품 ID (UUID)

      ## 동작 설명
      - 물품 Soft Delete 처리
      - 관련 이미지, 임베딩, 좋아요 기록 등 연관 데이터 삭제

      ## 반환값
      - 성공 시 상태코드 200 (OK)와 빈 응답 본문

      ## 에러코드
      - **`ITEM_NOT_FOUND`**: 해당 물품을 찾을 수 없습니다.
      """
  )
  ResponseEntity<Void> deleteItem(UUID itemId);

  // ==================== Members ====================

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.27", author = Author.SUHSAECHAN, issueNumber = 552, description = "관리자용 회원 단건 조회 API 추가"),
  })
  @Operation(
      summary = "관리자용 회원 단건 조회",
      description = """
      ## 인증(JWT): **필요** (관리자)

      ## 요청 파라미터 (AdminRequest)
      - **`memberId`**: 조회할 회원 ID (UUID, 필수)

      ## 반환값 (AdminResponse)
      - **`member`**: 회원 Entity 전체 정보
        - **`memberId`**: 회원 ID
        - **`email`**: 이메일
        - **`nickname`**: 닉네임
        - **`profileUrl`**: 프로필 이미지 URL
        - **`accountStatus`**: 계정 상태
        - **`socialPlatform`**: 소셜 로그인 플랫폼
        - **`role`**: 권한
        - **`totalLikeCount`**: 받은 좋아요 수
        - **`createdDate`**: 가입일
        - **`lastActiveAt`**: 마지막 활동 시간

      ## 에러코드
      - **`MEMBER_NOT_FOUND`**: 해당 회원을 찾을 수 없습니다.
      """
  )
  ResponseEntity<AdminResponse> getMemberDetail(AdminRequest request);

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.27", author = Author.SUHSAECHAN, issueNumber = 0, description = "관리자용 회원 목록 조회 API 구현"),
  })
  @Operation(
      summary = "관리자용 회원 목록 조회",
      description = """
      ## 인증(JWT): **필요** (관리자)

      ## 요청 파라미터 (AdminRequest)
      - **`pageNumber`**: 페이지 번호 (기본값: 0)
      - **`pageSize`**: 페이지 크기 (기본값: 20)
      - **`sortBy`**: 정렬 필드 (기본값: createdDate)
      - **`sortDirection`**: 정렬 방향 (기본값: DESC)
      - **`searchKeyword`**: 검색 키워드 (닉네임, 이메일)

      ## 반환값 (AdminResponse)
      - **`members`**: 회원 목록 (Page<AdminMemberDto>)
      - **`totalCount`**: 전체 회원 수
      """
  )
  ResponseEntity<AdminResponse> getMembers(AdminRequest request);

  // ==================== Reports ====================

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.27", author = Author.SUHSAECHAN, issueNumber = 0, description = "관리자 신고 관리 API 구현"),
  })
  @Operation(
      summary = "관리자 신고 관리 (Action 기반)",
      description = """
      ## 인증(JWT): **필요** (관리자)

      ## 요청 파라미터 (AdminReportRequest)
      - **`action`**: 수행할 동작
        - `item-list`: 물품 신고 목록 조회
        - `member-list`: 회원 신고 목록 조회
        - `item-detail`: 물품 신고 상세 조회
        - `member-detail`: 회원 신고 상세 조회
        - `update-status`: 신고 상태 변경
        - `stats`: 신고 통계 조회

      ## 반환값 (AdminReportResponse)
      - action에 따라 반환값 상이
      """
  )
  ResponseEntity<AdminReportResponse> handleReports(AdminReportRequest request);
}
