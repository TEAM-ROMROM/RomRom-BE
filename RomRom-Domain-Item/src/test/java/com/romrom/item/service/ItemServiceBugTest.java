package com.romrom.item.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.superLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.web.RomBackApplication;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class ItemServiceBugTest {

  @Autowired
  ItemService itemService;

  @Autowired
  ItemRepository itemRepository;

  @Autowired
  MemberRepository memberRepository;

  @Test
  public void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::deleteItem_softDelete_isDeletedTrue설정_테스트);
    lineLog(null);
    timeLog(this::getItemDetail_삭제된물품조회시_DELETED_ITEM반환_테스트);
    lineLog(null);
    timeLog(this::findItemById_삭제된물품조회시_ITEM_NOT_FOUND반환_테스트);
    lineLog(null);

    lineLog("테스트종료");
  }

  // Bug 2: deleteItem → softDelete (isDeleted=true) 처리 검증
  @Transactional
  public void deleteItem_softDelete_isDeletedTrue설정_테스트() {
    Item availableItem = itemRepository.findAll().stream()
        .filter(i -> !i.getIsDeleted())
        .findFirst()
        .orElse(null);

    if (availableItem == null) {
      lineLog("활성 물품이 없어 테스트 스킵");
      return;
    }

    UUID itemId = availableItem.getItemId();
    Member owner = availableItem.getMember();

    ItemRequest request = ItemRequest.builder()
        .member(owner)
        .itemId(itemId)
        .build();

    itemService.deleteItem(request);

    Item afterDelete = itemRepository.findById(itemId)
        .orElseThrow(() -> new RuntimeException("물품이 DB에서 완전히 삭제됨 - hardDelete 버그"));

    superLog(afterDelete.getIsDeleted());
    Assertions.assertTrue(afterDelete.getIsDeleted(),
        "deleteItem() 후 isDeleted=true 이어야 함 (softDelete)");
    lineLog("deleteItem softDelete 검증 완료");
  }

  // Bug 2 연계: getItemDetail 에서 삭제된 물품 조회 시 DELETED_ITEM 에러 반환
  @Transactional
  public void getItemDetail_삭제된물품조회시_DELETED_ITEM반환_테스트() {
    Item deletedItem = itemRepository.findAll().stream()
        .filter(Item::getIsDeleted)
        .findFirst()
        .orElse(null);

    if (deletedItem == null) {
      lineLog("삭제된 물품이 없어 테스트 스킵");
      return;
    }

    Member anyMember = memberRepository.findAll().stream()
        .filter(m -> !m.getIsDeleted())
        .findFirst()
        .orElseThrow(() -> new RuntimeException("활성 회원 없음"));

    ItemRequest request = ItemRequest.builder()
        .member(anyMember)
        .itemId(deletedItem.getItemId())
        .build();

    CustomException ex = Assertions.assertThrows(CustomException.class,
        () -> itemService.getItemDetail(request));

    superLog(ex.getErrorCode());
    Assertions.assertEquals(ErrorCode.DELETED_ITEM, ex.getErrorCode(),
        "삭제된 물품 조회 시 DELETED_ITEM 반환해야 함");
    lineLog("삭제된 물품 getItemDetail DELETED_ITEM 검증 완료");
  }

  // Bug 11: findItemById - 삭제된 물품 조회 시 ITEM_NOT_FOUND 반환 (수정 후)
  @Transactional
  public void findItemById_삭제된물품조회시_ITEM_NOT_FOUND반환_테스트() {
    Item deletedItem = itemRepository.findAll().stream()
        .filter(Item::getIsDeleted)
        .findFirst()
        .orElse(null);

    if (deletedItem == null) {
      lineLog("삭제된 물품이 없어 테스트 스킵");
      return;
    }

    UUID deletedItemId = deletedItem.getItemId();
    CustomException ex = Assertions.assertThrows(CustomException.class,
        () -> itemService.findItemById(deletedItemId));

    superLog(ex.getErrorCode());
    Assertions.assertEquals(ErrorCode.ITEM_NOT_FOUND, ex.getErrorCode(),
        "삭제된 물품 findItemById 시 ITEM_NOT_FOUND 반환해야 함 (Bug 11 수정 검증)");
    lineLog("findItemById 삭제된물품 ITEM_NOT_FOUND 검증 완료");
  }
}
