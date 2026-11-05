package com.romrom.notification.campaign;

import com.romrom.common.constant.DeviceType;
import java.util.Map;

/**
 * 대상 회원 SQL 조각 (member 별칭 m): 대상 조건, 푸시 수신 동의 조건, 바인딩 값, 토큰 기기 종류(선택)
 */
public record PushCampaignTargetQuery(
    String targetWhereClause,
    String pushConsentClause,
    Map<String, Object> parameters,
    DeviceType tokenDeviceType
) {
}
