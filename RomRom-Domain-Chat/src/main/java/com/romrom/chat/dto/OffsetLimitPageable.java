package com.romrom.chat.dto;

import org.springframework.data.domain.AbstractPageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * offset과 limit을 독립적으로 지정할 수 있는 Pageable.
 *
 * 표준 {@link org.springframework.data.domain.PageRequest}는 offset이 페이지번호 × 페이지크기로 고정돼,
 * 페이지크기만 늘리면 offset까지 함께 밀려 페이지 경계가 어긋난다.
 * 한 페이지를 채우기 위해 요청 크기보다 한 건 더 조회하면서도 offset은 그대로 유지해야 할 때 사용한다.
 */
public class OffsetLimitPageable extends AbstractPageRequest {

  private final long offset;
  private final Sort sort;

  private OffsetLimitPageable(long offset, int limit, Sort sort) {
    // AbstractPageRequest의 페이지 계산 로직은 사용하지 않으므로 page는 0으로 둔다.
    super(0, limit);
    this.offset = offset;
    this.sort = sort;
  }

  public static OffsetLimitPageable of(long offset, int limit, Sort sort) {
    return new OffsetLimitPageable(offset, limit, sort);
  }

  @Override
  public long getOffset() {
    return offset;
  }

  @Override
  public Sort getSort() {
    return sort;
  }

  @Override
  public Pageable next() {
    return OffsetLimitPageable.of(offset + getPageSize(), getPageSize(), sort);
  }

  @Override
  public Pageable previous() {
    long previousOffset = Math.max(offset - getPageSize(), 0);
    return OffsetLimitPageable.of(previousOffset, getPageSize(), sort);
  }

  @Override
  public Pageable first() {
    return OffsetLimitPageable.of(0, getPageSize(), sort);
  }

  @Override
  public Pageable withPage(int pageNumber) {
    return OffsetLimitPageable.of((long) pageNumber * getPageSize(), getPageSize(), sort);
  }
}
