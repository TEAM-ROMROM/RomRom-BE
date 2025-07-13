package com.romrom.application.service;

import static com.romrom.auth.jwt.JwtUtil.REFRESH_KEY_PREFIX;
import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.superLogDebug;

import com.github.javafaker.Faker;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.jwt.JwtUtil;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemTradeOption;
import com.romrom.common.constant.Role;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.suhsaechan.suhnicknamegenerator.core.SuhRandomKit;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestService {

  private final MemberRepository memberRepository;
  private final ItemRepository itemRepository;
  private final ItemImageRepository itemImageRepository;
  private final JwtUtil jwtUtil;
  private final RedisTemplate<String, Object> redisTemplate;
  private final SuhRandomKit suhRandomKit = SuhRandomKit.builder().locale("ko").uuidLength(4).numberLength(4).build();
  private final Faker koFaker = new Faker(new Locale("ko", "KR"));
  private final Faker enFaker = new Faker(new Locale("en"));
  private static final String MOCK_IMAGE_URL = "https://picsum.photos/300/400";

  /**
   * 회원 이메일로 가짜 로그인 처리 회원이 없으면 신규 가입 후, isFirstLogin 설정
   */
  @Transactional

  public TestResponse testSignIn(TestRequest request) {
    boolean isFirstLogin = false;
    String email = request.getEmail();

    // 이메일에 해당하는 회원 조회, 없으면 신규 가입 처리
    Member member = memberRepository.findByEmail(email)
        .orElseGet(() -> {

          Member newMember = Member.builder()
              .email(email)
              .socialPlatform(request.getSocialPlatform())
              .nickname(suhRandomKit.nicknameWithNumber())
              .profileUrl("TEST")
              .role(Role.ROLE_USER)
              .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
              .isFirstItemPosted(false)
              .build();

          Member savedMember = memberRepository.save(newMember);

          // 신규 가입시 첫 로그인으로 설정
          savedMember.setIsFirstLogin(true);
          return savedMember;
        });

    // JWT 토큰 생성
    CustomUserDetails customUserDetails = new CustomUserDetails(member);
    String accessToken = jwtUtil.createAccessToken(customUserDetails);
    String refreshToken = jwtUtil.createRefreshToken(customUserDetails);

    // RefreshToken -> Redis 저장 (키: "RT:{memberId}")
    redisTemplate.opsForValue().set(
        REFRESH_KEY_PREFIX + customUserDetails.getMemberId(),
        refreshToken,
        jwtUtil.getRefreshExpirationTime(),
        TimeUnit.MILLISECONDS
    );

    lineLog("가짜 로그인 성공: email=" + email);
    superLogDebug(member);

    return TestResponse.builder()
        .member(member)
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .isFirstLogin(member.getIsFirstLogin())
        .isFirstItemPosted(member.getIsFirstItemPosted())
        .build();
  }

  // count 만큼 Mock 사용자 생성
  @Transactional
  public void createMockMembers(Integer count) {
    List<Member> mockMembers = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      try {
        mockMembers.add(createMockMember());
      } catch (Exception e) {
        log.error("Mock 사용자 생성 실패: {}", e.getMessage());
      }
    }
    log.debug("Mock 사용자 {}명 생성 완료", mockMembers.size());
  }

  /**
   * Mock Member 생성 DataFaker 로 이메일, 닉네임, 프로필 URL, 소셜 플랫폼, 역할, 계정 상태를 생성
   *
   * @return 생성된 Member 객체
   */
  @Transactional
  public Member createMockMember() {
    Member mockMember = Member.builder()
        .email(enFaker.internet().emailAddress())
        .nickname(suhRandomKit.nicknameWithNumber())
        .profileUrl(MOCK_IMAGE_URL)
        .socialPlatform(enFaker.options().option(SocialPlatform.class))
        .role(Role.ROLE_USER)
        .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
        .build();

    // Mock Member 저장
    memberRepository.save(mockMember);
    return mockMember;
  }

  // count 만큼 Mock 물품 생성
  @Transactional
  public void createMockItems(Integer count) {
    List<Item> mockItems = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      try {
        mockItems.add(createMockItem());
      } catch (Exception e) {
        log.error("Mock 물품 생성 실패: {}", e.getMessage());
      }
    }
    log.debug("Mock 물품 {}개 생성 완료", mockItems.size());
  }

  /**
   * Mock Item 생성 DataFaker 로 물품명, 물품 설명, 카테고리, 상태, 거래 옵션을 생성
   *
   * @return 생성된 Item 객체
   */
  @Transactional
  public Item createMockItem() {
    Member mockMember = createMockMember();

    List<ItemTradeOption> tradeOptions = Stream.generate(() -> enFaker.options().option(ItemTradeOption.class))
        .distinct()
        .limit(enFaker.number().numberBetween(1, 3))
        .toList();

    Item mockItem = Item.builder()
        .member(mockMember)
        .itemName(koFaker.commerce().productName())
        .itemDescription(koFaker.lorem().sentence())
        .itemCategory(enFaker.options().option(ItemCategory.class))
        .itemCondition(enFaker.options().option(ItemCondition.class))
        .itemTradeOptions(tradeOptions)
        .likeCount(enFaker.number().numberBetween(0, 100))
        .price(enFaker.number().numberBetween(10, 1001) * 100)
        .build();

    // Mock Item 저장
    itemRepository.save(mockItem);
    mockMember.setIsFirstItemPosted(true);

    // 1~10개 사이의 Mock ItemImage 생성
    createMockItemImages(mockItem, enFaker.number().numberBetween(1, 11));

    return mockItem;
  }

  /**
   * Mock ItemImage 생성 DataFaker 로 이미지 URL, 파일 경로, 원본 파일명, 업로드된 파일명, 파일 크기를 생성
   *
   * @param item  Item 객체
   * @param count 생성할 Mock ItemImage 개수
   */
  @Transactional
  public void createMockItemImages(Item item, int count) {
    List<ItemImage> mockItemImages = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ItemImage mockItemImage = ItemImage.builder()
          .item(item)
          .filePath(MOCK_IMAGE_URL)
          .build();
      mockItemImages.add(mockItemImage);

      // Mock ItemImage 저장
      itemImageRepository.save(mockItemImage);
    }
    log.debug("Mock ItemImage {}개 생성 완료: itemId={}", mockItemImages.size(), item.getItemId());
  }
}
