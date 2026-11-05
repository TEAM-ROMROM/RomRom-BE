package com.romrom.common.annotation;

import com.romrom.common.constant.AdminAuditAction;
import com.romrom.common.constant.AdminAuditTargetType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 관리자 작업 기록 대상 API 표시 (#874): 메서드 정상 반환 시에만 admin_audit_log 1행 기록
 * targetIdParameter: @ModelAttribute 요청 객체의 속성 이름 (예: "itemId", 목록이면 첫 값 + 건수)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminAudit {

  AdminAuditAction action();

  AdminAuditTargetType targetType();

  String targetIdParameter() default "";
}
