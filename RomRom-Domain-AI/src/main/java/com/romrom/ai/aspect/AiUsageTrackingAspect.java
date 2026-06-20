package com.romrom.ai.aspect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.ai.annotation.AiTracked;
import com.romrom.ai.service.AiUsageHistoryService;
import com.romrom.common.constant.AiUsageType;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AiUsageTrackingAspect {

  private static final ExpressionParser SPEL_EXPRESSION_PARSER = new SpelExpressionParser();

  private final AiUsageHistoryService aiUsageHistoryService;
  private final ObjectMapper objectMapper;

  @Around("@annotation(aiTracked)")
  public Object trackAiUsage(ProceedingJoinPoint joinPoint, AiTracked aiTracked) throws Throwable {
    long aiInvocationStartMs = System.currentTimeMillis();
    Object methodReturnValue = null;
    Throwable methodThrowable = null;
    boolean isInvocationSuccess = true;
    String capturedErrorMessage = null;

    try {
      methodReturnValue = joinPoint.proceed();
      return methodReturnValue;
    } catch (Throwable thrownException) {
      methodThrowable = thrownException;
      isInvocationSuccess = false;
      capturedErrorMessage = thrownException.getClass().getSimpleName() + ": " + thrownException.getMessage();
      throw thrownException;
    } finally {
      long responseDurationMs = System.currentTimeMillis() - aiInvocationStartMs;
      try {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method targetMethod = methodSignature.getMethod();
        Object[] methodArgumentValues = joinPoint.getArgs();

        Map<String, Object> requestPayloadMap = buildRequestPayloadMap(methodSignature, methodArgumentValues);
        Map<String, Object> responsePayloadMap = buildResponsePayloadMap(methodReturnValue, methodThrowable);

        UUID extractedMemberId = extractMemberIdFromSecurityContext();
        UUID extractedRelatedEntityId = evaluateRelatedEntityIdSpEL(
            aiTracked.relatedEntityIdSpEL(), targetMethod, methodArgumentValues
        );

        String resolvedModelName = aiTracked.modelName().isBlank() ? null : aiTracked.modelName();
        AiUsageType resolvedAiUsageType = aiTracked.aiUsageType();

        aiUsageHistoryService.record(
            extractedMemberId,
            resolvedAiUsageType,
            extractedRelatedEntityId,
            requestPayloadMap,
            responsePayloadMap,
            isInvocationSuccess,
            capturedErrorMessage,
            responseDurationMs,
            resolvedModelName
        );
      } catch (Exception aspectException) {
        log.warn("AiUsageTrackingAspect 기록 실패 (원래 메서드 결과는 영향 없음): {}", aspectException.getMessage());
      }
    }
  }

  private Map<String, Object> buildRequestPayloadMap(MethodSignature methodSignature, Object[] methodArgumentValues) {
    Map<String, Object> requestPayloadMap = new LinkedHashMap<>();
    if (methodArgumentValues == null || methodArgumentValues.length == 0) {
      return requestPayloadMap;
    }
    String[] resolvedParameterNames = methodSignature.getParameterNames();
    for (int argIndex = 0; argIndex < methodArgumentValues.length; argIndex++) {
      String parameterDisplayName = (resolvedParameterNames != null && argIndex < resolvedParameterNames.length)
          ? resolvedParameterNames[argIndex]
          : "arg" + argIndex;
      requestPayloadMap.put(parameterDisplayName, methodArgumentValues[argIndex]);
    }
    return requestPayloadMap;
  }

  private Map<String, Object> buildResponsePayloadMap(Object methodReturnValue, Throwable methodThrowable) {
    Map<String, Object> responsePayloadMap = new LinkedHashMap<>();
    if (methodThrowable != null) {
      return responsePayloadMap;
    }
    if (methodReturnValue == null) {
      return responsePayloadMap;
    }
    try {
      Map<String, Object> convertedReturnMap = objectMapper.convertValue(
          methodReturnValue, new TypeReference<Map<String, Object>>() {}
      );
      if (convertedReturnMap != null) {
        return convertedReturnMap;
      }
    } catch (Exception conversionException) {
      // fallback below
    }
    responsePayloadMap.put("raw", String.valueOf(methodReturnValue));
    return responsePayloadMap;
  }

  private UUID extractMemberIdFromSecurityContext() {
    try {
      Authentication currentAuthentication = SecurityContextHolder.getContext().getAuthentication();
      if (currentAuthentication == null || !currentAuthentication.isAuthenticated()) {
        return null;
      }
      Object authenticatedPrincipal = currentAuthentication.getPrincipal();
      if (authenticatedPrincipal == null) {
        return null;
      }
      return extractMemberIdViaReflection(authenticatedPrincipal);
    } catch (Exception securityContextException) {
      return null;
    }
  }

  private UUID extractMemberIdViaReflection(Object principalObject) {
    // CustomUserDetails 형태: getMember().getMemberId()
    try {
      Method getMemberMethod = principalObject.getClass().getMethod("getMember");
      Object memberObject = getMemberMethod.invoke(principalObject);
      if (memberObject != null) {
        Method getMemberIdMethod = memberObject.getClass().getMethod("getMemberId");
        Object memberIdObject = getMemberIdMethod.invoke(memberObject);
        if (memberIdObject instanceof UUID memberUuid) {
          return memberUuid;
        }
      }
    } catch (NoSuchMethodException ignored) {
      // try next strategy
    } catch (Exception reflectionException) {
      return null;
    }
    // principal 자체가 memberId getter 를 가지는 경우 fallback
    try {
      Method getMemberIdDirect = principalObject.getClass().getMethod("getMemberId");
      Object memberIdValue = getMemberIdDirect.invoke(principalObject);
      if (memberIdValue instanceof UUID directUuid) {
        return directUuid;
      }
      if (memberIdValue instanceof String stringMemberId && !stringMemberId.isBlank()) {
        return UUID.fromString(stringMemberId);
      }
    } catch (Exception ignored) {
      // give up
    }
    return null;
  }

  private UUID evaluateRelatedEntityIdSpEL(String spelExpression, Method targetMethod, Object[] methodArgumentValues) {
    if (spelExpression == null || spelExpression.isBlank()) {
      return null;
    }
    try {
      EvaluationContext spelEvaluationContext = new StandardEvaluationContext();
      String[] reflectionParameterNames = resolveParameterNames(targetMethod);
      for (int paramIndex = 0; paramIndex < methodArgumentValues.length; paramIndex++) {
        String spelVariableName = (reflectionParameterNames != null && paramIndex < reflectionParameterNames.length)
            ? reflectionParameterNames[paramIndex]
            : "arg" + paramIndex;
        ((StandardEvaluationContext) spelEvaluationContext).setVariable(spelVariableName, methodArgumentValues[paramIndex]);
      }
      Expression parsedSpelExpression = SPEL_EXPRESSION_PARSER.parseExpression(spelExpression);
      Object evaluatedSpelValue = parsedSpelExpression.getValue(spelEvaluationContext);
      if (evaluatedSpelValue instanceof UUID resolvedUuid) {
        return resolvedUuid;
      }
      if (evaluatedSpelValue instanceof String stringValue && !stringValue.isBlank()) {
        return UUID.fromString(stringValue);
      }
      return null;
    } catch (Exception spelException) {
      log.debug("relatedEntityIdSpEL 평가 실패: expression={}, error={}", spelExpression, spelException.getMessage());
      return null;
    }
  }

  private String[] resolveParameterNames(Method targetMethod) {
    java.lang.reflect.Parameter[] reflectionParameters = targetMethod.getParameters();
    String[] parameterNames = new String[reflectionParameters.length];
    for (int paramIndex = 0; paramIndex < reflectionParameters.length; paramIndex++) {
      parameterNames[paramIndex] = reflectionParameters[paramIndex].getName();
    }
    return parameterNames;
  }
}
