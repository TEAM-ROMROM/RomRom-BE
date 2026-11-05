package com.romrom.notification.campaign;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.romrom.notification.repository.PushCampaignOpenRepository;
import com.romrom.notification.repository.PushCampaignRepository;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * 푸시 열람 기록 단위 테스트 (#874): 회원·캠페인 1회, 없는 캠페인·형식 오류 무시, DB 오류도 예외 없음
 */
class PushCampaignOpenServiceTest {

  private PushCampaignRepository pushCampaignRepository;
  private PushCampaignOpenRepository pushCampaignOpenRepository;
  private PushCampaignOpenService pushCampaignOpenService;

  @BeforeEach
  void setUp() {
    pushCampaignRepository = mock(PushCampaignRepository.class);
    pushCampaignOpenRepository = mock(PushCampaignOpenRepository.class);
    pushCampaignOpenService = new PushCampaignOpenService(pushCampaignRepository, pushCampaignOpenRepository);
  }

  @Test
  @DisplayName("같은 회원이 두 번 열어도 열람 수는 1회만 증가")
  void recordOpen_멱등() {
    UUID campaignId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    when(pushCampaignRepository.existsById(campaignId)).thenReturn(true);
    when(pushCampaignOpenRepository.insertIfAbsent(any(), eq(campaignId), eq(memberId), any())).thenReturn(1).thenReturn(0);

    Assertions.assertTrue(pushCampaignOpenService.recordOpen(memberId, campaignId.toString()));
    Assertions.assertFalse(pushCampaignOpenService.recordOpen(memberId, campaignId.toString()));
    verify(pushCampaignRepository, times(1)).incrementOpenedCount(campaignId);
  }

  @Test
  @DisplayName("없는 캠페인 > 기록 없이 false")
  void recordOpen_없는캠페인() {
    UUID campaignId = UUID.randomUUID();
    when(pushCampaignRepository.existsById(campaignId)).thenReturn(false);

    Assertions.assertFalse(pushCampaignOpenService.recordOpen(UUID.randomUUID(), campaignId.toString()));
    verifyNoInteractions(pushCampaignOpenRepository);
  }

  @Test
  @DisplayName("campaignId 누락·형식 오류 > 조회 없이 false")
  void recordOpen_형식오류() {
    Assertions.assertFalse(pushCampaignOpenService.recordOpen(UUID.randomUUID(), null));
    Assertions.assertFalse(pushCampaignOpenService.recordOpen(UUID.randomUUID(), "not-a-uuid"));
    verifyNoInteractions(pushCampaignRepository, pushCampaignOpenRepository);
  }

  @Test
  @DisplayName("DB 오류 > 예외 전파 없이 false (앱 응답 200 유지)")
  void recordOpen_DB오류() {
    UUID campaignId = UUID.randomUUID();
    when(pushCampaignRepository.existsById(campaignId)).thenThrow(new DataAccessResourceFailureException("down"));

    Assertions.assertFalse(pushCampaignOpenService.recordOpen(UUID.randomUUID(), campaignId.toString()));
    verify(pushCampaignRepository, never()).incrementOpenedCount(any());
  }
}
