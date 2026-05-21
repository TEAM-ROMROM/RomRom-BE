package com.romrom.ai.annotation;

import com.romrom.common.constant.AiUsageType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiTracked {
  AiUsageType aiUsageType();
  String modelName() default "";
  String relatedEntityIdSpEL() default "";
}
