package com.romrom.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 오픈 API에 HMAC + Timestamp 기반 서명 검증을 적용하는 어노테이션
 * 클래스 레벨 또는 메서드 레벨에 적용 가능
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SecuredApi {
}
