package com.romrom.chat.service;

import com.romrom.chat.entity.mongo.ChatAppointment;
import com.romrom.chat.entity.mongo.ChatAppointmentStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 약속 글자 표시: 상태별 말머리, 오전·오후 시각
 */
class ChatAppointmentContentFormatterTest {

  @Test
  @DisplayName("제안 > [약속] 11월 6일 (금) 오후 3:00 · 군자역, 확정·취소 말머리 변경")
  void 상태별_본문() {
    ChatAppointment appointment = ChatAppointment.propose(LocalDateTime.of(2026, 11, 6, 15, 0), "군자역", null, null,
        UUID.randomUUID());
    Assertions.assertEquals("[약속] 11월 6일 (금) 오후 3:00 · 군자역",
        ChatAppointmentContentFormatter.toMessageContent(appointment));

    appointment.changeStatus(ChatAppointmentStatus.ACCEPTED, UUID.randomUUID());
    Assertions.assertTrue(ChatAppointmentContentFormatter.toMessageContent(appointment).startsWith("[약속 확정] "));

    appointment.changeStatus(ChatAppointmentStatus.CANCELED, UUID.randomUUID());
    Assertions.assertTrue(ChatAppointmentContentFormatter.toMessageContent(appointment).startsWith("[약속 취소] "));
  }

  @Test
  @DisplayName("정오 > 오후 12:00, 자정 > 오전 12:00, 오전 9시 30분 > 오전 9:30")
  void 오전_오후_시각() {
    Assertions.assertEquals("오후 12:00", ChatAppointmentContentFormatter.toTimeLabel(LocalDateTime.of(2026, 11, 6, 12, 0)));
    Assertions.assertEquals("오전 12:00", ChatAppointmentContentFormatter.toTimeLabel(LocalDateTime.of(2026, 11, 6, 0, 0)));
    Assertions.assertEquals("오전 9:30", ChatAppointmentContentFormatter.toTimeLabel(LocalDateTime.of(2026, 11, 6, 9, 30)));
  }
}
