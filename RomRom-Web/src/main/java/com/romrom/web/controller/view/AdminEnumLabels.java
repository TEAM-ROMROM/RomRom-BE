package com.romrom.web.controller.view;

import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.AiUsageType;
import com.romrom.common.constant.DeviceType;
import com.romrom.common.constant.ItemAdminDeleteReason;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.LoginResult;
import com.romrom.common.constant.Role;
import com.romrom.common.constant.SanctionType;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.common.constant.TradeStatus;
import com.romrom.report.enums.ItemReportReason;
import com.romrom.report.enums.MemberReportReason;
import com.romrom.report.enums.ReportStatus;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 관리자 화면 enum 표시 이름: 서버 enum description 단일 원본 > 화면 전역 AdminLabels
 * 형태: { "ItemCategory": { "WOMEN_CLOTHING": "여성의류", ... }, ... } (선언 순서 유지)
 */
public final class AdminEnumLabels {

  private static final Map<String, Map<String, String>> LABELS_BY_ENUM_NAME = buildLabelsByEnumName();

  private AdminEnumLabels() {
  }

  public static Map<String, Map<String, String>> getLabelsByEnumName() {
    return LABELS_BY_ENUM_NAME;
  }

  private static Map<String, Map<String, String>> buildLabelsByEnumName() {
    Map<String, Map<String, String>> labelsByEnumName = new LinkedHashMap<>();
    labelsByEnumName.put("AccountStatus", toLabelByCode(AccountStatus.values(), AccountStatus::getDescription));
    labelsByEnumName.put("Role", toLabelByCode(Role.values(), Role::getDescription));
    labelsByEnumName.put("SocialPlatform", toLabelByCode(SocialPlatform.values(), SocialPlatform::getDescription));
    labelsByEnumName.put("DeviceType", toLabelByCode(DeviceType.values(), DeviceType::getDescription));
    labelsByEnumName.put("LoginResult", toLabelByCode(LoginResult.values(), LoginResult::getDescription));
    labelsByEnumName.put("SanctionType", toLabelByCode(SanctionType.values(), SanctionType::getDescription));
    labelsByEnumName.put("ItemCategory", toLabelByCode(ItemCategory.values(), ItemCategory::getDescription));
    labelsByEnumName.put("ItemCondition", toLabelByCode(ItemCondition.values(), ItemCondition::getDescription));
    labelsByEnumName.put("ItemAdminDeleteReason", toLabelByCode(ItemAdminDeleteReason.values(), ItemAdminDeleteReason::getDescription));
    labelsByEnumName.put("ItemStatus", toLabelByCode(ItemStatus.values(), ItemStatus::getDescription));
    labelsByEnumName.put("TradeStatus", toLabelByCode(TradeStatus.values(), TradeStatus::getDescription));
    labelsByEnumName.put("ReportStatus", toLabelByCode(ReportStatus.values(), ReportStatus::getDescription));
    labelsByEnumName.put("ItemReportReason", toLabelByCode(ItemReportReason.values(), ItemReportReason::getDescription));
    labelsByEnumName.put("MemberReportReason", toLabelByCode(MemberReportReason.values(), MemberReportReason::getDescription));
    labelsByEnumName.put("AiUsageType", toLabelByCode(AiUsageType.values(), AiUsageType::getDescription));
    return Collections.unmodifiableMap(labelsByEnumName);
  }

  private static <E extends Enum<E>> Map<String, String> toLabelByCode(E[] enumValues, Function<E, String> toDescription) {
    Map<String, String> labelByCode = new LinkedHashMap<>();
    Arrays.stream(enumValues).forEach(enumValue -> labelByCode.put(enumValue.name(), toDescription.apply(enumValue)));
    return Collections.unmodifiableMap(labelByCode);
  }
}
