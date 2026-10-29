package com.romrom.common.dto;

/**
 * 그룹별 건수 집계 결과 (native 쿼리 별칭 group_key·count)
 */
public interface GroupCountProjection {

  String getGroupKey();

  Long getCount();
}
