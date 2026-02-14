# Design: 좋아요한 물품 조회 시 거래완료된 물품 제외

**Issue**: [#515](https://github.com/TEAM-ROMROM/RomRom-BE/issues/515)
**Date**: 2026-02-14

## Problem

`getLikedItems()` API가 거래완료(`EXCHANGED`) 상태의 물품도 반환하고 있음. 일반 물품 목록(`getItemList()`)에서는 이미 `ItemStatus.AVAILABLE` 필터링이 적용되어 있지만, 좋아요 목록 조회에는 이 필터가 누락됨.

## Decision

`ItemRepository.findByItemIdInAndIsDeletedFalse()` JPQL 쿼리의 WHERE 절에 `AND i.itemStatus = 'AVAILABLE'` 조건 추가.

### Alternatives Considered

| Approach | Verdict |
|----------|---------|
| A. JPQL 쿼리에 조건 추가 | **채택** - 최소 변경, DB 레벨 필터링 |
| B. 조건 추가 + 메서드명 변경 | 불필요한 변경 범위 확대 |
| C. ItemStatus 파라미터 전달 | YAGNI - 현재 이 메서드는 좋아요 조회 전용 |

## Change

**File**: `RomRom-Domain-Item/.../repository/postgres/ItemRepository.java`

JPQL 쿼리 WHERE 절에 1줄 추가:
```
AND i.itemStatus = 'AVAILABLE'
```

서비스/컨트롤러 코드 변경 없음.
