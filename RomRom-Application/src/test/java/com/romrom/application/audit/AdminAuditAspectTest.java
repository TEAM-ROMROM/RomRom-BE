package com.romrom.application.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.application.dto.AdminRequest;
import com.romrom.application.service.AdminAuditLogService;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.annotation.AdminAudit;
import com.romrom.common.constant.AdminAuditAction;
import com.romrom.common.constant.AdminAuditTargetType;
import com.romrom.common.entity.postgres.AdminAuditLog;
import com.romrom.member.entity.Member;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 관리자 작업 기록 AOP 단위 테스트 (#874): 성공 시에만 기록, 실패 요청 미기록, 기록 실패 삼킴, 관리자·대상·IP 채움
 */
class AdminAuditAspectTest {

  private static final UUID ADMIN_MEMBER_ID = UUID.randomUUID();
  private static final String ADMIN_EMAIL = "admin@romrom.app";

  private AdminAuditLogService adminAuditLogService;
  private FakeAdminController fakeAdminControllerProxy;

  /**
   * 관리자 컨트롤러 대역: 정상 처리, 실패, 목록 대상
   */
  static class FakeAdminController {

    @AdminAudit(action = AdminAuditAction.ITEM_DELETE, targetType = AdminAuditTargetType.ITEM, targetIdParameter = "itemId")
    public String deleteItem(@ModelAttribute AdminRequest request, Object principal) {
      return "deleted";
    }

    @AdminAudit(action = AdminAuditAction.ITEM_HIDE, targetType = AdminAuditTargetType.ITEM, targetIdParameter = "itemId")
    public String hideItemFailing(@ModelAttribute AdminRequest request) {
      throw new IllegalStateException("business failure");
    }

    @AdminAudit(action = AdminAuditAction.MEMBER_BULK_SUSPEND, targetType = AdminAuditTargetType.MEMBER, targetIdParameter = "memberIds")
    public String bulkSuspend(@ModelAttribute AdminRequest request) {
      return "suspended";
    }
  }

  @BeforeEach
  void setUp() {
    adminAuditLogService = mock(AdminAuditLogService.class);
    AdminAuditAspect adminAuditAspect = new AdminAuditAspect(adminAuditLogService,
        new AdminAuditRequestSummarizer(new ObjectMapper()));
    AspectJProxyFactory aspectJProxyFactory = new AspectJProxyFactory(new FakeAdminController());
    aspectJProxyFactory.setProxyTargetClass(true);
    aspectJProxyFactory.addAspect(adminAuditAspect);
    fakeAdminControllerProxy = aspectJProxyFactory.getProxy();

    Member adminMember = Member.builder().memberId(ADMIN_MEMBER_ID).email(ADMIN_EMAIL).build();
    CustomUserDetails adminUserDetails = new CustomUserDetails(adminMember);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(adminUserDetails, null, List.of()));
    MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
    mockHttpServletRequest.addHeader("X-Real-IP", "203.0.113.7");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockHttpServletRequest));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  @DisplayName("정상 반환: 관리자·작업·대상·요약·IP·요청 요약(비밀값 ***) 1행 기록")
  void recordsAfterSuccessfulReturn() {
    UUID itemId = UUID.randomUUID();
    AdminRequest adminRequest = AdminRequest.builder().itemId(itemId).password("should-not-appear").build();

    String methodResult = fakeAdminControllerProxy.deleteItem(adminRequest, new Object());

    Assertions.assertEquals("deleted", methodResult);
    ArgumentCaptor<AdminAuditLog> adminAuditLogCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
    verify(adminAuditLogService).record(adminAuditLogCaptor.capture());
    AdminAuditLog recordedAdminAuditLog = adminAuditLogCaptor.getValue();
    Assertions.assertEquals(ADMIN_MEMBER_ID, recordedAdminAuditLog.getAdminMemberId());
    Assertions.assertEquals(ADMIN_EMAIL, recordedAdminAuditLog.getAdminEmail());
    Assertions.assertEquals(AdminAuditAction.ITEM_DELETE, recordedAdminAuditLog.getAction());
    Assertions.assertEquals(AdminAuditTargetType.ITEM, recordedAdminAuditLog.getTargetType());
    Assertions.assertEquals(itemId.toString(), recordedAdminAuditLog.getTargetId());
    Assertions.assertEquals("물품 삭제", recordedAdminAuditLog.getSummary());
    Assertions.assertEquals("203.0.113.7", recordedAdminAuditLog.getClientIp());
    Assertions.assertTrue(recordedAdminAuditLog.getRequestSummary().contains(itemId.toString()));
    Assertions.assertFalse(recordedAdminAuditLog.getRequestSummary().contains("should-not-appear"));
  }

  @Test
  @DisplayName("실패한 요청: 예외 그대로 전파, 기록 없음")
  void skipsRecordWhenMethodThrows() {
    AdminRequest adminRequest = AdminRequest.builder().itemId(UUID.randomUUID()).build();

    Assertions.assertThrows(IllegalStateException.class, () -> fakeAdminControllerProxy.hideItemFailing(adminRequest));
    verify(adminAuditLogService, never()).record(any());
  }

  @Test
  @DisplayName("기록 저장 실패: 관리자 작업 결과는 그대로 반환")
  void swallowsAuditWriteFailure() {
    doThrow(new RuntimeException("database down")).when(adminAuditLogService).record(any());
    AdminRequest adminRequest = AdminRequest.builder().itemId(UUID.randomUUID()).build();

    String methodResult = Assertions.assertDoesNotThrow(() -> fakeAdminControllerProxy.deleteItem(adminRequest, new Object()));

    Assertions.assertEquals("deleted", methodResult);
  }

  @Test
  @DisplayName("목록 대상: 첫 ID + 요약에 건수")
  void recordsFirstTargetIdAndCountForCollections() {
    UUID firstMemberId = UUID.randomUUID();
    AdminRequest adminRequest = AdminRequest.builder()
        .memberIds(List.of(firstMemberId, UUID.randomUUID(), UUID.randomUUID()))
        .build();

    fakeAdminControllerProxy.bulkSuspend(adminRequest);

    ArgumentCaptor<AdminAuditLog> adminAuditLogCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
    verify(adminAuditLogService).record(adminAuditLogCaptor.capture());
    Assertions.assertEquals(firstMemberId.toString(), adminAuditLogCaptor.getValue().getTargetId());
    Assertions.assertEquals("회원 일괄 정지 (3건)", adminAuditLogCaptor.getValue().getSummary());
  }
}
