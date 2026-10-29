package com.romrom.common.dto;

/**
 * 날짜별 건수 집계 결과 (native 쿼리 별칭 day·count)
 * day 형식: yyyy-MM-dd (DB 서버 시간대 = KST)
 */
public interface DailyCountProjection {

  String getDay();

  Long getCount();
}
