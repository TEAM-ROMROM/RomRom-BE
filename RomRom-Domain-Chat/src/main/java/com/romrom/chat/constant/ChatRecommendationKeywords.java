package com.romrom.chat.constant;

import java.util.List;
import java.util.Set;

public final class ChatRecommendationKeywords {

  public static final List<String> LOCATION_DIRECT_REQUEST_KEYWORDS = List.of(
      "주소",
      "위치",
      "좌표",
      "어디서",
      "어디에서",
      "어디로",
      "만날 곳",
      "만나는 곳",
      "장소",
      "직거래",
      "픽업"
  );

  public static final List<String> LOCATION_DIRECT_COMMIT_KEYWORDS = List.of(
      "주소 드릴게",
      "주소보낼게",
      "주소 보내드릴게",
      "위치 드릴게",
      "위치보낼게",
      "위치 보내드릴게",
      "좌표 드릴게",
      "좌표보낼게",
      "공유드릴게"
  );

  public static final List<String> LOCATION_SIGNAL_KEYWORDS = List.of(
      "예약",
      "만나요",
      "만날게요",
      "오실",
      "오시는",
      "도착",
      "찾기"
  );

  public static final List<String> TRADE_COMPLETION_KEYWORDS = List.of(
      "고생하셨",
      "수고하셨",
      "감사합니다",
      "감사했",
      "감사드려요",
      "좋은 거래",
      "잘 받았",
      "잘받았",
      "잘 쓸게",
      "잘쓸게",
      "마무리",
      "거래 완료",
      "완료해"
  );

  public static final List<String> REJECT_KEYWORDS = List.of(
      "취소",
      "거절",
      "보류",
      "실수",
      "잘못",
      "다시 해주세요",
      "다음에",
      "안 할게"
  );

  public static final Set<String> TRIVIAL_TEXTS = Set.of(
      "ㅇㅇ", "ㅇ", "응", "웅", "넵", "넹", "네", "네네", "예", "넹넹",
      "ok", "okay", "오케이", "ㅇㅋ", "ㅋㅋ", "ㅎㅎ", "ㅠㅠ", "ㅜㅜ"
  );

  private ChatRecommendationKeywords() {
  }
}
