package com.romrom.chat.service;

import com.romrom.chat.entity.mongo.ChatAppointment;
import com.romrom.chat.entity.mongo.ChatAppointmentStatus;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 약속 > 글자 표시 (옛 앱 말풍선 본문·푸시 본문): [약속] 11월 6일 (목) 오후 3:00 · 군자역
 */
public final class ChatAppointmentContentFormatter {

  private static final DateTimeFormatter MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN);
  private static final DateTimeFormatter HOUR_MINUTE_FORMATTER = DateTimeFormatter.ofPattern("h:mm", Locale.KOREAN);

  private ChatAppointmentContentFormatter() {
  }

  /**
   * 상태별 말머리 + 일시 + 장소 (PROPOSED [약속], ACCEPTED [약속 확정], CANCELED [약속 취소])
   */
  public static String toMessageContent(ChatAppointment appointment) {
    return toStatusPrefix(appointment.getStatus()) + " " + toDateTimeLabel(appointment.getAppointmentAt())
        + " · " + appointment.getPlaceName();
  }

  /**
   * 일시 > 11월 6일 (목) 오후 3:00
   */
  public static String toDateTimeLabel(LocalDateTime appointmentAt) {
    String dayOfWeekLabel = appointmentAt.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
    return appointmentAt.format(MONTH_DAY_FORMATTER) + " (" + dayOfWeekLabel + ") " + toTimeLabel(appointmentAt);
  }

  /**
   * 시각 > 오전·오후 h:mm (정오 > 오후 12:00, 자정 > 오전 12:00)
   */
  public static String toTimeLabel(LocalDateTime appointmentAt) {
    String meridiemLabel = appointmentAt.getHour() < 12 ? "오전" : "오후";
    return meridiemLabel + " " + appointmentAt.format(HOUR_MINUTE_FORMATTER);
  }

  private static String toStatusPrefix(ChatAppointmentStatus status) {
    return switch (status) {
      case PROPOSED -> "[약속]";
      case ACCEPTED -> "[약속 확정]";
      case CANCELED -> "[약속 취소]";
    };
  }
}
