package com.romrom.application.audit;

import com.romrom.application.service.AdminAuditLogService;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.annotation.AdminAudit;
import com.romrom.common.entity.postgres.AdminAuditLog;
import com.romrom.member.entity.Member;
import com.romrom.common.util.ClientIpAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 관리자 작업 기록 AOP (#874): @AdminAudit 메서드 정상 반환 > 관리자·대상·요청 요약·IP 1행 기록
 * 실패한 요청은 기록 없음, 기록 실패는 경고 로그만 (관리자 작업 결과에 영향 없음)
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AdminAuditAspect {

  private static final int[] NO_PARAMETER_INDEXES = new int[0];

  private final AdminAuditLogService adminAuditLogService;
  private final AdminAuditRequestSummarizer adminAuditRequestSummarizer;
  // 메서드별 @ModelAttribute 파라미터 위치 (1회 계산 후 캐시)
  private final Map<Method, int[]> modelAttributeIndexesByMethod = new ConcurrentHashMap<>();

  @Around("@annotation(adminAudit)")
  public Object recordAdminAudit(ProceedingJoinPoint joinPoint, AdminAudit adminAudit) throws Throwable {
    // 실패 시 예외 그대로 전파 > 기록 없음
    Object methodReturnValue = joinPoint.proceed();
    recordSafely(joinPoint, adminAudit);
    return methodReturnValue;
  }

  // 기록 실패는 삼킴 (성공한 관리자 작업을 실패로 바꾸지 않음)
  private void recordSafely(ProceedingJoinPoint joinPoint, AdminAudit adminAudit) {
    try {
      adminAuditLogService.record(buildAdminAuditLog(joinPoint, adminAudit));
    } catch (Exception auditException) {
      log.warn("[admin audit] record failed : action={}, cause={}: {}", adminAudit.action(),
          auditException.getClass().getSimpleName(), auditException.getMessage());
    }
  }

  private AdminAuditLog buildAdminAuditLog(ProceedingJoinPoint joinPoint, AdminAudit adminAudit) {
    Method signatureMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
    Class<?> targetClass = joinPoint.getTarget() != null ? joinPoint.getTarget().getClass() : signatureMethod.getDeclaringClass();
    List<Object> requestObjects = collectModelAttributeArguments(signatureMethod, targetClass, joinPoint.getArgs());
    Map<String, Object> propertyValueByName = adminAuditRequestSummarizer.readPropertyValues(requestObjects);
    Object targetIdValue = adminAudit.targetIdParameter().isBlank()
        ? null : propertyValueByName.get(adminAudit.targetIdParameter());
    Member adminMember = findCurrentAdminMember();

    return AdminAuditLog.builder()
        .adminMemberId(adminMember != null ? adminMember.getMemberId() : null)
        .adminEmail(adminMember != null ? adminMember.getEmail() : null)
        .action(adminAudit.action())
        .targetType(adminAudit.targetType())
        .targetId(toTargetId(targetIdValue))
        .summary(toSummary(adminAudit, targetIdValue))
        .requestSummary(adminAuditRequestSummarizer.toRequestSummaryJson(propertyValueByName))
        .clientIp(AdminAuditRequestSummarizer.truncate(findClientIp(), AdminAuditLog.CLIENT_IP_MAXIMUM_LENGTH))
        .build();
  }

  /**
   * 대상 값 > 대상 ID (목록이면 첫 값, 최대 100자)
   */
  static String toTargetId(Object targetIdValue) {
    Object firstTargetIdValue = targetIdValue;
    if (targetIdValue instanceof Collection<?> targetIdCollection) {
      firstTargetIdValue = targetIdCollection.isEmpty() ? null : targetIdCollection.iterator().next();
    }
    if (firstTargetIdValue == null) {
      return null;
    }
    String targetIdText = String.valueOf(firstTargetIdValue);
    return targetIdText.length() > AdminAuditLog.TARGET_ID_MAXIMUM_LENGTH
        ? targetIdText.substring(0, AdminAuditLog.TARGET_ID_MAXIMUM_LENGTH) : targetIdText;
  }

  /**
   * 작업 이름 > 요약 (목록 대상이면 "(N건)" 추가)
   */
  static String toSummary(AdminAudit adminAudit, Object targetIdValue) {
    String actionDescription = adminAudit.action().getDescription();
    if (targetIdValue instanceof Collection<?> targetIdCollection) {
      return actionDescription + " (" + targetIdCollection.size() + "건)";
    }
    return actionDescription;
  }

  // 구현 클래스 메서드 기준 (인터페이스 Docs 메서드에는 @ModelAttribute 없음)
  private List<Object> collectModelAttributeArguments(Method signatureMethod, Class<?> targetClass, Object[] methodArguments) {
    int[] modelAttributeIndexes = modelAttributeIndexesByMethod.computeIfAbsent(signatureMethod,
        method -> findModelAttributeIndexes(AopUtils.getMostSpecificMethod(method, targetClass)));
    List<Object> requestObjects = new ArrayList<>(modelAttributeIndexes.length);
    for (int parameterIndex : modelAttributeIndexes) {
      if (parameterIndex < methodArguments.length) {
        requestObjects.add(methodArguments[parameterIndex]);
      }
    }
    return requestObjects;
  }

  private static int[] findModelAttributeIndexes(Method targetMethod) {
    Annotation[][] parameterAnnotations = targetMethod.getParameterAnnotations();
    List<Integer> modelAttributeIndexes = new ArrayList<>();
    for (int parameterIndex = 0; parameterIndex < parameterAnnotations.length; parameterIndex++) {
      for (Annotation parameterAnnotation : parameterAnnotations[parameterIndex]) {
        if (parameterAnnotation instanceof ModelAttribute) {
          modelAttributeIndexes.add(parameterIndex);
          break;
        }
      }
    }
    return modelAttributeIndexes.isEmpty()
        ? NO_PARAMETER_INDEXES : modelAttributeIndexes.stream().mapToInt(Integer::intValue).toArray();
  }

  private static Member findCurrentAdminMember() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails customUserDetails) {
      return customUserDetails.getMember();
    }
    return null;
  }

  private static String findClientIp() {
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
      return null;
    }
    HttpServletRequest httpServletRequest = servletRequestAttributes.getRequest();
    return ClientIpAddressResolver.resolveClientIpAddress(httpServletRequest);
  }
}
