package com.romrom.member.service;

import com.romrom.ai.service.EmbeddingService;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.dto.AdminRequest;
import com.romrom.common.dto.AdminResponse;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.dto.MemberResponse;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberItemCategory;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberBlockRepository;
import com.romrom.member.repository.MemberItemCategoryRepository;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.member.repository.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

  private final MemberRepository memberRepository;
  private final MemberLocationRepository memberLocationRepository;
  private final MemberItemCategoryRepository memberItemCategoryRepository;
  private final MemberBlockRepository memberBlockRepository;
  private final EmbeddingService embeddingService;

  /**
   * 사용자 정보 반환
   */
  @Transactional(readOnly = true)
  public MemberResponse getMemberInfo(Member member) {
    MemberLocation memberLocation = memberLocationRepository.findByMemberMemberId(member.getMemberId())
      .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_LOCATION_NOT_FOUND));

    List<MemberItemCategory> memberItemCategory = memberItemCategoryRepository.findByMemberMemberId(member.getMemberId());

    return MemberResponse.builder()
      .member(member)
      .memberLocation(memberLocation)
      .memberItemCategories(memberItemCategory)
      .build();
  }

  /**
   * memberId로 회원 정보 반환 (타인 프로필 조회)
   */
  @Transactional(readOnly = true)
  public MemberResponse getMemberInfoById(MemberRequest request) {
    UUID currentMemberId = request.getMember().getMemberId();
    UUID memberId = request.getMemberId();
    Member member = memberRepository.findById(memberId)
      .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    member.setOnlineIfActiveWithin90Seconds();
    MemberLocation memberLocation = memberLocationRepository.findByMemberMemberId(memberId)
      .orElseGet(() -> {
        log.warn("회원 위치 정보 없음: memberId={}", memberId);
        return null;
      });

    // 위치 주소 세팅
    if (memberLocation != null) {
      member.setLocationAddress(memberLocation.getFullAddress());
    }

    // isBlocked 세팅 (내가 해당 회원을 차단했는지)
    boolean isBlocked = memberBlockRepository.existsByBlockerAndBlocked(currentMemberId, memberId);
    member.setIsBlocked(isBlocked);

    List<MemberItemCategory> memberItemCategory = memberItemCategoryRepository.findByMemberMemberId(memberId);

    return MemberResponse.builder()
      .member(member)
      .memberLocation(memberLocation)
      .memberItemCategories(memberItemCategory)
      .build();
  }

  /**
   * 회원 선호 카테고리 리스트 저장
   */
  @Transactional
  public void saveMemberItemCategories(MemberRequest request) {
    // 회원 정보 추출
    Member member = request.getMember();

    // 기존 선호 카테고리 삭제
    memberItemCategoryRepository.deleteByMemberMemberId(member.getMemberId());

    // 새로운 선호 카테고리 생성 및 저장
    List<MemberItemCategory> preferences = new ArrayList<>();
    for (int code : request.getPreferredCategories()) {
      ItemCategory itemCategory = ItemCategory.fromCode(code);
      MemberItemCategory preference = MemberItemCategory.builder()
        .member(member)
        .itemCategory(itemCategory)
        .build();
      preferences.add(preference);
    }

    List<MemberItemCategory> memberItemCategories = memberItemCategoryRepository.saveAll(preferences);

    // 회원 선호 카테고리 저장 완료
    member.setIsItemCategorySaved(true);
    memberRepository.save(member);

    // 회원 선호 카테고리 임베딩 생성 및 저장
    String categoryText = memberItemCategories.stream()
        .map(mic -> mic.getItemCategory().name())
        .collect(Collectors.joining(", "));
    embeddingService.generateAndSaveMemberItemCategoryEmbedding(member.getMemberId(), categoryText);

    log.info("회원 선호 카테고리 저장 완료: memberId={}, 카테고리 수={}", member.getMemberId(), memberItemCategories.size());
  }

  /**
   * 회원 관련 데이터 삭제 (Member 도메인 내 데이터만)
   * Application Service에서 호출되는 순수 도메인 로직
   */
  @Transactional
  public void deleteMemberRelatedData(MemberRequest request) {
    Member member = request.getMember();

    // 회원 위치정보 삭제 (hardDelete)
    memberLocationRepository.deleteByMemberMemberId(member.getMemberId());

    // 회원 선호 카테고리 삭제 (hardDelete)  
    memberItemCategoryRepository.deleteByMemberMemberId(member.getMemberId());

    // 회원 이메일, 닉네임 초기화
    member.setEmail(null);
    //member.setNickname(null); // 채팅방 조회시 탈퇴한 회원도 닉네임이 필요하므로 주석처리
    memberRepository.save(member);
  }

  /**
   * 이용약관 동의
   * 마케팅 정보 수신 동의 여부 및 필수 이용약관 동의 여부를 저장합니다
   *
   * @param request accessToken, refreshToken, isMarketingInfoAgreed
   */
  @Transactional
  public MemberResponse saveTermsAgreement(MemberRequest request) {
    Member member = request.getMember();
    member.setIsRequiredTermsAgreed(true);
    member.setIsMarketingInfoAgreed(request.getIsMarketingInfoAgreed());
    member.setIsActivityNotificationAgreed(request.getIsMarketingInfoAgreed()); // 알림 수신 동의도 동일하게 설정
    member.setIsChatNotificationAgreed(request.getIsMarketingInfoAgreed()); // 알림 수신 동의도 동일하게 설정
    member.setIsContentNotificationAgreed(request.getIsMarketingInfoAgreed()); // 알림 수신 동의도 동일하게 설정
    member.setIsTradeNotificationAgreed(request.getIsMarketingInfoAgreed()); // 알림 수신 동의도 동일하게 설정
    Member savedMember = memberRepository.save(member);

    return MemberResponse.builder()
      .member(savedMember)
      .build();
  }

  /**
   * 알림 수신 동의 여부 업데이트
   */
  @Transactional
  public MemberResponse updateNotificationAgreed(MemberRequest request) {
    Member member = request.getMember();
    applyIfNotNull(request.getIsMarketingInfoAgreed(), member.getIsMarketingInfoAgreed(), member::setIsMarketingInfoAgreed);
    applyIfNotNull(request.getIsActivityNotificationAgreed(), member.getIsActivityNotificationAgreed(), member::setIsActivityNotificationAgreed);
    applyIfNotNull(request.getIsChatNotificationAgreed(), member.getIsChatNotificationAgreed(), member::setIsChatNotificationAgreed);
    applyIfNotNull(request.getIsContentNotificationAgreed(), member.getIsContentNotificationAgreed(), member::setIsContentNotificationAgreed);
    applyIfNotNull(request.getIsTradeNotificationAgreed(), member.getIsTradeNotificationAgreed(), member::setIsTradeNotificationAgreed);
    return MemberResponse.builder()
        .member(memberRepository.save(member))
        .build();
  }

  /**
   * 탐색 범위 설정
   */
  @Transactional
  public void setSearchRadius(MemberRequest request) {
    Member member = request.getMember();
    member.setSearchRadiusInMeters(request.getSearchRadiusInMeters());
    memberRepository.save(member);
  }

  /**
   * 회원 프로필 변경
   * 닉네임 및 프로필 사진 변경
   */
  @Transactional
  public void updateMemberProfile(MemberRequest request) {

    Member member = request.getMember();
    String newNickname = request.getNickname();

    // 닉네임 변경
    if (StringUtils.hasText(newNickname) &&
        !newNickname.equals(member.getNickname())) {

      // 중복 검사
      if (memberRepository.existsByNicknameAndMemberIdNot(newNickname, member.getMemberId())) {
        log.warn("닉네임 중복: {}", newNickname);
        throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
      }

      member.setNickname(newNickname);
    }

    // 프로필 URL 변경
    if (StringUtils.hasText(request.getProfileUrl())) {
      member.setProfileUrl(request.getProfileUrl());
    }

    memberRepository.save(member);
  }

  /**
   * 활성 회원 수 조회 (관리자용)
   */
  @Transactional(readOnly = true)
  public long countActiveMembers() {
    return memberRepository.countActiveMembers();
  }

  /**
   * 모든 회원 목록 조회 (관리자용)
   */
  @Transactional(readOnly = true)
  public List<Member> getAllMembers() {
    return memberRepository.findAll();
  }

  /**
   * 관리자용 회원 목록 조회 (페이지네이션, 검색 지원)
   */
  @Transactional(readOnly = true)
  public AdminResponse getMembersForAdmin(AdminRequest request) {
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(request.getSortDirection(), request.getSortBy())
    );

    Page<Member> memberPage;
    if (StringUtils.hasText(request.getSearchKeyword())) {
      memberPage = memberRepository.searchByKeywordAndIsDeletedFalse(
          request.getSearchKeyword().trim(), pageable);
    } else {
      memberPage = memberRepository.findByIsDeletedFalse(pageable);
    }

    Page<AdminResponse.AdminMemberDto> adminMemberDtoPage = memberPage.map(member ->
        AdminResponse.AdminMemberDto.builder()
            .memberId(member.getMemberId())
            .nickname(member.getNickname())
            .profileUrl(member.getProfileUrl())
            .email(member.getEmail())
            .isActive(!member.getIsDeleted())
            .createdDate(member.getCreatedDate())
            .lastLoginDate(member.getUpdatedDate())
            .build()
    );

    return AdminResponse.builder()
        .members(adminMemberDtoPage)
        .totalCount(memberPage.getTotalElements())
        .build();
  }

  /**
   * 최근 가입 회원 조회 (관리자 대시보드용)
   */
  @Transactional(readOnly = true)
  public AdminResponse getRecentMembersForAdmin(int limit) {
    Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdDate"));
    Page<Member> memberPage = memberRepository.findByIsDeletedFalse(pageable);

    // 회원 DTO 변환
    Page<AdminResponse.AdminMemberDto> adminMemberDtoPage = memberPage.map(member ->
      AdminResponse.AdminMemberDto.builder()
        .memberId(member.getMemberId())
        .nickname(member.getNickname())
        .profileUrl(member.getProfileUrl())
        .email(member.getEmail())
        .isActive(!member.getIsDeleted())
        .createdDate(member.getCreatedDate())
        .lastLoginDate(member.getUpdatedDate()) // 임시로 updatedDate 사용
        .build()
    );

    return AdminResponse.builder()
      .members(adminMemberDtoPage)
      .totalCount((long) adminMemberDtoPage.getContent().size())
      .build();
  }

  /**
   * 관리자용 회원 단건 조회
   */
  @Transactional(readOnly = true)
  public AdminResponse getMemberDetailForAdmin(AdminRequest request) {
    Member member = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> {
          log.error("관리자 회원 단건 조회 실패 - 존재하지 않는 memberId: {}", request.getMemberId());
          return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        });

    return AdminResponse.builder()
        .member(member)
        .build();
  }

  /**
   * PK 기반 회원조회
   */
  @Transactional(readOnly = true)
  public Member findMemberById(UUID memberId) {
    return memberRepository.findById(memberId)
      .orElseThrow(() -> {
        log.error("PK: {}에 해당하는 회원을 찾을 수 없습니다.", memberId);
        return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
      });
  }

  /**
   * newValue가 null 이면 유지
   * newValue가 현재랑 동일하면 no-op
   */
  private void applyIfNotNull(Boolean newValue, Boolean currentValue, Consumer<Boolean> setter) {
    if (newValue == null) {
      return;
    }
    if (newValue.equals(currentValue)) {
      return;
    }
    setter.accept(newValue);
  }
}