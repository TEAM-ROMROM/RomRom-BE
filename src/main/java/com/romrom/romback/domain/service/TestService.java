package com.romrom.romback.domain.service;

import static com.romrom.romback.global.jwt.JwtUtil.REFRESH_KEY_PREFIX;
import static com.romrom.romback.global.util.LogUtil.lineLog;
import static com.romrom.romback.global.util.LogUtil.superLogDebug;

import com.github.javafaker.Faker;
import com.romrom.romback.domain.object.constant.AccountStatus;
import com.romrom.romback.domain.object.constant.ItemCategory;
import com.romrom.romback.domain.object.constant.ItemCondition;
import com.romrom.romback.domain.object.constant.ItemTradeOption;
import com.romrom.romback.domain.object.constant.Role;
import com.romrom.romback.domain.object.constant.SocialPlatform;
import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.dto.TestRequest;
import com.romrom.romback.domain.object.dto.TestResponse;
import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.ItemImage;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.repository.postgres.ItemImageRepository;
import com.romrom.romback.domain.repository.postgres.ItemRepository;
import com.romrom.romback.domain.repository.postgres.MemberRepository;
import com.romrom.romback.global.jwt.JwtUtil;
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
    for (int i = 0; i < count; i++) {
      try {
        createMockMember();
      } catch (Exception e) {
        log.error("Mock 사용자 생성 실패: {}", e.getMessage());
      }
    }
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
        .profileUrl(enFaker.internet().image())
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
    for (int i = 0; i < count; i++) {
      try {
        createMockItem();
      } catch (Exception e) {
        log.error("Mock 물품 생성 실패: {}", e.getMessage());
      }
    }
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

    ItemImage mockItemImage = ItemImage.builder()
        .item(mockItem)
        .imageUrl(enFaker.internet().image())
        .filePath("/mock/path/" + enFaker.file().fileName())
        .originalFileName(enFaker.file().fileName())
        .uploadedFileName("mock_" + enFaker.file().fileName())
        .fileSize(enFaker.number().numberBetween(10000L, 500000L))
        .build();

    // Mock ItemImage 저장
    itemImageRepository.save(mockItemImage);
    return mockItem;
  }
}
