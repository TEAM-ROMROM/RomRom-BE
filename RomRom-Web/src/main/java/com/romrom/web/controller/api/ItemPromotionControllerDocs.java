package com.romrom.web.controller.api;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

/**
 * 물품 롬업(우선 노출) API 문서: 활성화, 사전 자격 조회
 */
public interface ItemPromotionControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.10.31", author = Author.SUHSAECHAN, issueNumber = 819, description = "보상형 광고 시청 후 내 물건 롬업(24시간 우선 노출) 활성화 API 추가"),
  })
  @Operation(
      summary = "내 물건 롬업(우선 노출) 활성화",
      description = """
          ## 인증(JWT): **필요**

          ## 요청 파라미터 (ItemRequest, multipart/form-data)
          - **`itemId (UUID)`**: 롬업할 내 물품 ID

          ## 반환값 (ItemResponse)
          - **`item`**: 갱신된 물품 (`promotionExpiresAt` = 지금 + 24시간, `isPromoted` = true)

          ## 설명
          - 보상형 광고 시청 완료 후 앱이 호출 (서버 광고 보상 검증(AdMob SSV)은 미적용, 아래 한도로 남용 제한)
          - 롬업 24시간 동안 홈 피드에 일반 카드 3장마다 1장씩 다른 회원(선호 카테고리·탐색 반경 일치)에게 노출
          - 한도: 같은 물품은 24시간에 1회, 회원은 하루(한국 시간 0시 기준) 최대 3회

          ## 에러코드
          - **`ITEM_NOT_FOUND`** (404): 물품을 찾을 수 없습니다.
          - **`INVALID_ITEM_OWNER`** (403): 본인 물품이 아닙니다.
          - **`ITEM_NOT_PROMOTABLE`** (400): 교환 가능(AVAILABLE) 상태가 아니거나 관리자 노출 차단 물품
          - **`ITEM_PROMOTION_ALREADY_ACTIVE`** (409): 이 물품을 24시간 안에 이미 롬업함
          - **`ITEM_PROMOTION_DAILY_LIMIT_EXCEEDED`** (429): 오늘 롬업 3회를 모두 사용함
          """
  )
  ResponseEntity<ItemResponse> promoteItem(
      CustomUserDetails customUserDetails,
      ItemRequest request
  );

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 858, description = "롬업 사전 자격 조회 API 추가 (광고 시청 전 남은 횟수·물품 제한·다음 가능 시각 확인)"),
  })
  @Operation(
      summary = "내 물건 롬업(우선 노출) 사전 자격 조회",
      description = """
          ## 인증(JWT): **필요**

          ## 요청 파라미터 (ItemRequest, multipart/form-data)
          - **`itemId (UUID)`**: 롬업할 내 물품 ID

          ## 반환값 (ItemResponse.promotionEligibility)
          - **`isEligible`**: 지금 롬업 가능 여부
          - **`ineligibleReason`**: 불가 사유 errorCode (ITEM_NOT_PROMOTABLE / ITEM_PROMOTION_ALREADY_ACTIVE / ITEM_PROMOTION_DAILY_LIMIT_EXCEEDED, 가능하면 null)
          - **`dailyPromotionLimit`**: 하루 최대 횟수 (3)
          - **`remainingDailyPromotionCount`**: 오늘(한국 시간) 남은 횟수
          - **`itemCooldownEndsAt`**: 물품 24시간 제한 종료 일시 (없으면 null)
          - **`dailyLimitResetsAt`**: 하루 횟수 초기화 일시 (다음 한국 시간 0시)
          - **`nextAvailableAt`**: 다음 롬업 가능 일시 (지금 가능하거나 교환 불가 물품이면 null)

          ## 설명
          - 롬업 활성화 API(`/api/item/promote`)와 같은 기준, 저장 없음
          - 앱은 광고를 띄우기 전에 호출해 불가하면 광고 없이 안내

          ## 에러코드
          - **`ITEM_NOT_FOUND`** (404): 물품을 찾을 수 없습니다.
          - **`INVALID_ITEM_OWNER`** (403): 본인 물품이 아닙니다.
          """
  )
  ResponseEntity<ItemResponse> getPromotionEligibility(
      CustomUserDetails customUserDetails,
      ItemRequest request
  );
}
