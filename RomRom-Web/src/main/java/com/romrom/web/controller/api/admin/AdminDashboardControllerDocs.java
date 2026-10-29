package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminDashboardControllerDocs {

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.29", author = Author.SUHSAECHAN, issueNumber = 815, description = "totalItems에서 soft delete 물품 제외 (count() > countByIsDeletedFalse)"),
        @ApiChangeLog(date = "2026.06.04", author = Author.SUHSAECHAN, issueNumber = 714, description = "대시보드 통계에 거래 상태별 카운트(tradeStatusCounts, 데이터 주도) + 신규 후기 카운트(newReviewCount) 추가, startDate/endDate 기간 필터 지원"),
    })
    @Operation(
        summary = "관리자 대시보드 통계 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data, 모두 선택)
        - **`startDate`** (String): 기간 시작 (yyyy-MM-dd). 없으면 전체 누적
        - **`endDate`** (String): 기간 종료 (yyyy-MM-dd, 해당 일자 23:59:59 까지 포함)

        ## 반환값 (AdminResponse.dashboardStats)
        - **`totalMembers`**: 전체 활성 회원 수 (기간 무관 현재값)
        - **`totalItems`**: 전체 활성 물품 수 (삭제 물품 제외, 기간 무관 현재값)
        - **`ongoingTrades`**: 진행중 거래 건수 (기간 무관 현재값)
        - **`pendingReports`**: 미처리 신고 건수 (기간 무관 현재값)
        - **`tradeStatusCounts`**: 거래 상태별 건수 Map (모든 TradeStatus 키 포함, 0건도 노출). 기간 필터 적용 시 해당 기간 집계
        - **`newReviewCount`**: 신규 후기 건수. 기간 필터 적용 시 해당 기간 작성 후기 수
        """
    )
    ResponseEntity<AdminResponse> getDashboardStats(AdminRequest request);

    ResponseEntity<AdminResponse> getRecentMembers(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.04", author = Author.SUHSAECHAN, issueNumber = 714, description = "대시보드 최근 교환완료(TRADED) 거래 조회 API 추가"),
    })
    @Operation(
        summary = "관리자 대시보드 최근 교환완료 거래 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 반환값 (AdminResponse.recentTrades)
        - 최근 교환완료(TRADED) 거래 최신 8건 (takeItem/giveItem 및 각 소유 회원 정보 포함, updatedDate 내림차순)
        """
    )
    ResponseEntity<AdminResponse> getRecentTrades(AdminRequest request);

    ResponseEntity<AdminResponse> getRecentItems(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.08", author = Author.SUHSAECHAN, issueNumber = 793, description = "대시보드 동접자(온라인 사용자) 조회 API 추가: 앱 전체 동접(Redis heartbeat, 최근 5분 윈도우) + 채팅 온라인(현재 채팅방 접속 중) 두 지표"),
    })
    @Operation(
        summary = "관리자 대시보드 동접자(온라인 사용자) 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 동작 설명
        모바일 앱은 HTTP 요청이 stateless라 "현재 연결 여부"를 직접 알 수 없으므로,
        Last-Seen + 시간 윈도우(5분) 근사 방식으로 동접자를 집계한다.
        - 인증된 모든 API 요청이 Redis Sorted Set에 heartbeat로 기록되고, 조회 시 5분 초과 항목을 청소한 뒤 남은 수를 센다.
        - 다중 인스턴스(블루그린)에서도 공유 Redis로 합산이 정확하다.

        ## 반환값 (AdminResponse)
        - **`onlineMemberCount`** (Long): 앱 전체 동접자 수 (최근 5분 내 인증 API 호출한 고유 회원 수)
        - **`chatOnlineMemberCount`** (Long): 채팅 온라인 회원 수 (현재 채팅방 접속 중인 고유 회원 수)
        """
    )
    ResponseEntity<AdminResponse> getOnlineStats(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.29", author = Author.SUHSAECHAN, issueNumber = 827, description = "대시보드 운영 분석 API 추가: 기간(7·30·90일) 날짜별 추세, 직전 같은 길이 기간 대비 증감, DAU·WAU·MAU, 처리 대기, 분포(거래·신고 상태, 카테고리, 지역, 가입 경로, 기기, 후기 평점, AI 유형), 시간대별 접속"),
    })
    @Operation(
        summary = "관리자 대시보드 운영 분석 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 (multipart/form-data)
        - **`analyticsPeriodDays`** (Integer, 선택): 분석 기간(일, 오늘 포함). 1~180, 미입력 시 30. 범위 밖이면 400 `INVALID_REQUEST`

        ## 기간
        - 현재 기간: 오늘 포함 N일 (KST 날짜). 직전 기간: 현재 시작 전날까지 같은 N일
        - 날짜별 계열은 빈 날짜를 0으로 채워 `dates`와 같은 길이

        ## 반환값 (AdminResponse.dashboardAnalytics)
        - **`periodDays`**, **`startDate`**, **`endDate`**, **`dates`** (yyyy-MM-dd 목록)
        - **`dailySeries`** (Map<String, List<Long>>): newMembers(관리자 제외), newItems, tradeRequests, completedTrades(TRADED 상태의 마지막 수정일 기준 근사), newReviews, loginMembers(로그인 성공 고유 회원), chatMessages, itemViews, aiCalls, aiFailures
        - **`metrics`** (Map<String, {currentValue, previousValue, changeRate}>): 위 계열의 현재·직전 기간 합과 증감률(%, 직전 0이면 null). aiFailures 제외
        - **`totalMembers`**, **`dailyActiveMembers`**·**`weeklyActiveMembers`**·**`monthlyActiveMembers`** (lastActiveAt 1·7·30일 이내, 탈퇴·관리자 제외), **`activeItems`**, **`pendingReports`**, **`completeRequestedTrades`**: 기간 무관 현재값
        - 분포 (List<{key, label, count}>, 건수 많은 순)
          - 기간 내: **`tradeStatusCounts`**, **`reportStatusCounts`**(물품+회원), **`reviewRatingCounts`**, **`aiUsageTypeCounts`**, **`deviceTypeCounts`**(로그인 성공)
          - 현재: **`itemCategoryCounts`**(활성 물품), **`regionCounts`**(시·도 상위 10), **`socialPlatformCounts`**
        - **`loginHourlyCounts`** (List<Long>, 24칸): 기간 내 로그인 성공의 KST 시각별 건수

        ## 응답 예시
        ```json
        {
          "dashboardAnalytics": {
            "periodDays": 7,
            "startDate": "2026-10-23",
            "endDate": "2026-10-29",
            "dates": ["2026-10-23", "2026-10-24", "2026-10-25", "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29"],
            "dailySeries": { "newMembers": [0, 1, 0, 2, 0, 0, 3], "tradeRequests": [1, 0, 0, 4, 2, 0, 1] },
            "metrics": { "newMembers": { "currentValue": 6, "previousValue": 4, "changeRate": 50.0 } },
            "dailyActiveMembers": 12,
            "pendingReports": 2,
            "tradeStatusCounts": [ { "key": "CHATTING", "label": "채팅중", "count": 5 } ],
            "loginHourlyCounts": [0, 0, 0, 0, 0, 0, 1, 2, 3, 5, 4, 2, 6, 3, 2, 4, 5, 6, 8, 9, 7, 5, 3, 1]
          }
        }
        ```
        """
    )
    ResponseEntity<AdminResponse> getDashboardAnalytics(AdminRequest request);
}
