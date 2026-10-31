package com.romrom.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.chat.service.recommendation.ChatRecommendationSettings;
import com.romrom.common.constant.Role;
import com.romrom.common.constant.SystemConfigKeys;
import com.romrom.common.entity.postgres.SystemConfig;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.repository.EmbeddingRepository;
import com.romrom.common.repository.SystemConfigRepository;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemPromotionHistoryRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberBlockRepository;
import com.romrom.member.repository.MemberRepository;
import com.romrom.notification.repository.FcmTokenRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 관리자 운영 기능 회귀 검증 (#838): 프로필 수정, 롬업 종료, 설정 비밀 값 가림·입력 검증
 * Mockito 단위 테스트 (DB·Redis 불필요)
 */
@ExtendWith(MockitoExtension.class)
class AdminOperationManagementTest {

  @Mock private MemberRepository memberRepository;
  @Mock private MemberBlockRepository memberBlockRepository;
  @Mock private FcmTokenRepository fcmTokenRepository;
  @Mock private AdminMemberSanctionService adminMemberSanctionService;
  @Mock private ItemRepository itemRepository;
  @Mock private ItemPromotionHistoryRepository itemPromotionHistoryRepository;
  @Mock private EmbeddingRepository embeddingRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private SystemConfigRepository systemConfigRepository;
  @Mock private SystemConfigService systemConfigService;
  @Mock private ChatRecommendationSettings chatRecommendationSettings;

  @InjectMocks private AdminMemberManagementService adminMemberManagementService;
  @InjectMocks private AdminItemOperationService adminItemOperationService;
  @InjectMocks private AdminOperationSettingService adminOperationSettingService;

  private final UUID executorAdminId = UUID.randomUUID();

  @Test
  @DisplayName("프로필 수정: 다른 회원이 쓰는 닉네임은 DUPLICATE_NICKNAME, 저장 없음")
  void 중복_닉네임은_거부된다() {
    Member targetMember = Member.builder().memberId(UUID.randomUUID()).nickname("기존닉네임").role(Role.ROLE_USER).build();
    when(memberRepository.findById(targetMember.getMemberId())).thenReturn(Optional.of(targetMember));
    when(memberRepository.existsByNicknameAndMemberIdNot("사용중닉네임", targetMember.getMemberId())).thenReturn(true);

    CustomException profileException = assertThrows(CustomException.class,
        () -> adminMemberManagementService.updateMemberProfile(profileRequest(targetMember.getMemberId(), "사용중닉네임", false)));

    assertEquals(ErrorCode.DUPLICATE_NICKNAME, profileException.getErrorCode());
    verify(memberRepository, never()).save(any());
  }

  @Test
  @DisplayName("프로필 수정: 사진 초기화는 profileUrl null, 닉네임 공백이면 유지")
  void 사진_초기화는_profileUrl을_비운다() {
    Member targetMember = Member.builder().memberId(UUID.randomUUID()).nickname("기존닉네임")
        .profileUrl("https://image").role(Role.ROLE_USER).build();
    when(memberRepository.findById(targetMember.getMemberId())).thenReturn(Optional.of(targetMember));

    adminMemberManagementService.updateMemberProfile(profileRequest(targetMember.getMemberId(), " ", true));

    assertNull(targetMember.getProfileUrl());
    assertEquals("기존닉네임", targetMember.getNickname());
    verify(adminMemberSanctionService).validateAdminAction(targetMember.getMemberId(), executorAdminId);
  }

  @Test
  @DisplayName("롬업 종료: 롬업 중이 아닌 물품은 INVALID_REQUEST")
  void 롬업_중이_아닌_물품은_종료할_수_없다() {
    Item expiredPromotionItem = Item.builder().itemId(UUID.randomUUID()).promotionExpiresAt(LocalDateTime.now().minusHours(1)).build();
    when(itemRepository.findById(expiredPromotionItem.getItemId())).thenReturn(Optional.of(expiredPromotionItem));

    CustomException promotionException = assertThrows(CustomException.class,
        () -> adminItemOperationService.endItemPromotion(AdminRequest.builder().itemId(expiredPromotionItem.getItemId()).build()));

    assertEquals(ErrorCode.INVALID_REQUEST, promotionException.getErrorCode());
    verify(itemRepository, never()).save(any());
  }

  @Test
  @DisplayName("롬업 종료: 만료 일시를 지금으로 당김")
  void 롬업_종료는_만료_일시를_지금으로_바꾼다() {
    Item promotedItem = Item.builder().itemId(UUID.randomUUID()).promotionExpiresAt(LocalDateTime.now().plusHours(10)).build();
    when(itemRepository.findById(promotedItem.getItemId())).thenReturn(Optional.of(promotedItem));

    adminItemOperationService.endItemPromotion(AdminRequest.builder().itemId(promotedItem.getItemId()).build());

    assertTrue(promotedItem.getPromotionExpiresAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    verify(itemRepository).save(promotedItem);
  }

  @Test
  @DisplayName("전체 설정 조회: SMTP 비밀번호는 가리고 영속 엔티티 값은 그대로")
  void 비밀_설정값은_가린다() {
    SystemConfig smtpPasswordConfig = SystemConfig.builder().configKey(SystemConfigKeys.MAIL_SMTP_PASSWORD).configValue("real-secret").build();
    SystemConfig appVersionConfig = SystemConfig.builder().configKey(SystemConfigKeys.APP_MIN_VERSION).configValue("1.10.0").build();
    when(systemConfigRepository.findAll()).thenReturn(List.of(smtpPasswordConfig, appVersionConfig));

    AdminResponse systemConfigResponse = adminOperationSettingService.getAllSystemConfigs();

    SystemConfig displayedSmtpPassword = systemConfigResponse.getSystemConfigs().stream()
        .filter(systemConfig -> SystemConfigKeys.MAIL_SMTP_PASSWORD.equals(systemConfig.getConfigKey()))
        .findFirst().orElseThrow();
    assertEquals("******", displayedSmtpPassword.getConfigValue());
    assertEquals("real-secret", smtpPasswordConfig.getConfigValue());
  }

  @Test
  @DisplayName("채팅 추천 운영값: 0 이하·숫자 아님은 INVALID_REQUEST, 저장 없음")
  void 채팅_추천_초_값은_양수만_허용한다() {
    AdminRequest invalidSecondsRequest = AdminRequest.builder().chatRecommendationCacheTtlSeconds("0").build();

    CustomException settingException = assertThrows(CustomException.class,
        () -> adminOperationSettingService.updateChatRecommendationConfig(invalidSecondsRequest));

    assertEquals(ErrorCode.INVALID_REQUEST, settingException.getErrorCode());
    verify(systemConfigService, never()).upsertConfigValue(anyString(), anyString(), anyString());
  }

  private AdminRequest profileRequest(UUID targetMemberId, String nickname, boolean isProfileImageReset) {
    return AdminRequest.builder()
        .memberId(targetMemberId)
        .memberNickname(nickname)
        .isProfileImageReset(isProfileImageReset)
        .executorAdminId(executorAdminId)
        .build();
  }
}
