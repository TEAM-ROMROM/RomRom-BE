package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.jwt.JwtUtil;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.Role;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.suhsaechan.suhnicknamegenerator.core.SuhRandomKit;
import org.springframework.context.annotation.Profile;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"dev", "test"}) // dev/test 환경 전용 — prod 빈 등록 안 됨
@RequiredArgsConstructor
@Slf4j
public class DevToolsService {

  private final MemberRepository memberRepository;
  private final ItemRepository itemRepository;
  private final JwtUtil jwtUtil;

  private final SuhRandomKit suhRandomKit = SuhRandomKit.builder()
      .locale("ko").uuidLength(4).numberLength(4).build();

  /**
   * 테스트 회원 수동 생성 — ROLE_TEST 부여
   */
  @Transactional
  public AdminResponse createTestMember(AdminRequest request) {
    String email = request.getEmail() != null ? request.getEmail()
        : "test_" + System.currentTimeMillis() + "@dev.local";
    String nickname = request.getNickname() != null ? request.getNickname()
        : suhRandomKit.nicknameWithNumber();
    SocialPlatform platform = parseSocialPlatform(request.getSocialPlatform());

    if (memberRepository.findByEmail(email).isPresent()) {
      log.warn("이미 존재하는 이메일: {}", email);
      throw new CustomException(ErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    Member testMember = Member.builder()
        .email(email)
        .nickname(nickname)
        .socialPlatform(platform)
        .role(Role.ROLE_TEST)
        .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
        .isFirstLogin(false)
        .isItemCategorySaved(true)
        .isFirstItemPosted(true)
        .isMemberLocationSaved(true)
        .isRequiredTermsAgreed(true)
        .isMarketingInfoAgreed(false)
        .isActivityNotificationAgreed(false)
        .isChatNotificationAgreed(false)
        .isContentNotificationAgreed(false)
        .isTradeNotificationAgreed(false)
        .isDeleted(false)
        .build();

    memberRepository.save(testMember);
    log.info("[DevTools] 테스트 회원 생성: email={}, nickname={}, role=ROLE_TEST", email, nickname);

    return AdminResponse.builder()
        .member(testMember)
        .build();
  }

  /**
   * 회원 선택 후 accessToken 발급
   */
  @Transactional(readOnly = true)
  public AdminResponse issueDevToken(AdminRequest request) {
    Member member = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> {
          log.error("[DevTools] 토큰 발급 실패 - 존재하지 않는 memberId: {}", request.getMemberId());
          return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        });

    CustomUserDetails userDetails = new CustomUserDetails(member);
    String accessToken = jwtUtil.createAccessToken(userDetails);

    log.info("[DevTools] accessToken 발급: memberId={}, nickname={}", member.getMemberId(), member.getNickname());

    return AdminResponse.builder()
        .devAccessToken(accessToken)
        .member(member)
        .build();
  }

  /**
   * 테스트 물품 수동 생성
   */
  @Transactional
  public AdminResponse createTestItem(AdminRequest request) {
    Member member = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> {
          log.error("[DevTools] 물품 생성 실패 - 존재하지 않는 memberId: {}", request.getMemberId());
          return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        });

    ItemCategory category = request.getItemCategory() != null
        ? request.getItemCategory() : ItemCategory.OTHER;
    ItemCondition condition = request.getItemCondition() != null
        ? request.getItemCondition() : ItemCondition.MODERATELY_USED;
    String itemName = request.getItemName() != null ? request.getItemName() : "테스트 물품";
    int price = request.getPrice() != null ? request.getPrice() : 0;

    Item item = Item.builder()
        .member(member)
        .itemName(itemName)
        .itemDescription(request.getItemDescription())
        .itemCategory(category)
        .itemCondition(condition)
        .itemStatus(ItemStatus.AVAILABLE)
        .price(price)
        .isAiPredictedPrice(false)
        .isDeleted(false)
        .build();

    itemRepository.save(item);
    log.info("[DevTools] 테스트 물품 생성: itemId={}, name={}, memberId={}",
        item.getItemId(), itemName, member.getMemberId());

    return AdminResponse.builder()
        .item(item)
        .build();
  }

  /**
   * ROLE_TEST 회원 목록 조회 (토큰 발급/물품 등록 셀렉트 목록)
   */
  @Transactional(readOnly = true)
  public AdminResponse getTestMembers() {
    List<Member> testMembers = memberRepository.findByRoleOrderByCreatedDateDesc(Role.ROLE_TEST);
    Page<Member> memberPage = new PageImpl<>(testMembers,
        PageRequest.of(0, Math.max(testMembers.size(), 1)), testMembers.size());
    return AdminResponse.builder()
        .members(memberPage)
        .totalCount((long) testMembers.size())
        .build();
  }

  /**
   * 테스트 회원 랜덤 생성 — 모든 값 자동생성
   */
  @Transactional
  public AdminResponse createRandomTestMember() {
    String nickname = suhRandomKit.nicknameWithNumber();
    String email = "test_" + System.currentTimeMillis() + "@dev.local";

    Member testMember = Member.builder()
        .email(email)
        .nickname(nickname)
        .socialPlatform(SocialPlatform.KAKAO)
        .role(Role.ROLE_TEST)
        .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
        .isFirstLogin(false)
        .isItemCategorySaved(true)
        .isFirstItemPosted(true)
        .isMemberLocationSaved(true)
        .isRequiredTermsAgreed(true)
        .isMarketingInfoAgreed(false)
        .isActivityNotificationAgreed(false)
        .isChatNotificationAgreed(false)
        .isContentNotificationAgreed(false)
        .isTradeNotificationAgreed(false)
        .isDeleted(false)
        .build();

    memberRepository.save(testMember);
    log.info("[DevTools] 랜덤 테스트 회원 생성: email={}, nickname={}", email, nickname);

    return AdminResponse.builder()
        .member(testMember)
        .build();
  }

  private SocialPlatform parseSocialPlatform(String value) {
    if (value == null || value.isBlank()) return SocialPlatform.KAKAO;
    try {
      return SocialPlatform.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return SocialPlatform.KAKAO;
    }
  }
}
