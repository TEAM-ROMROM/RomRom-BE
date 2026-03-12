package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.report.entity.MemberReport;
import com.romrom.report.repository.MemberReportRepository;
import java.util.List;
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
public class AdminMemberService {

  private final MemberRepository memberRepository;
  private final ItemRepository itemRepository;
  private final MemberReportRepository memberReportRepository;

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
      log.debug("회원 목록 검색 조회: keyword={}, page={}, size={}",
          request.getSearchKeyword(), request.getPageNumber(), request.getPageSize());
      memberPage = memberRepository.searchByKeywordAndIsDeletedFalse(
          request.getSearchKeyword().trim(), pageable);
    } else {
      log.debug("회원 목록 전체 조회: page={}, size={}", request.getPageNumber(), request.getPageSize());
      memberPage = memberRepository.findByIsDeletedFalse(pageable);
    }

    log.info("회원 목록 조회 완료: totalElements={}, page={}/{}",
        memberPage.getTotalElements(), memberPage.getNumber(), memberPage.getTotalPages());

    return AdminResponse.builder()
        .members(memberPage)
        .totalCount(memberPage.getTotalElements())
        .build();
  }

  /**
   * 최근 가입 회원 조회 (관리자 대시보드용)
   */
  @Transactional(readOnly = true)
  public AdminResponse getRecentMembersForAdmin(int limit) {
    log.debug("최근 가입 회원 조회: limit={}", limit);
    Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdDate"));
    Page<Member> memberPage = memberRepository.findByIsDeletedFalse(pageable);

    log.info("최근 가입 회원 조회 완료: count={}", memberPage.getContent().size());

    return AdminResponse.builder()
        .members(memberPage)
        .totalCount(memberPage.getTotalElements())
        .build();
  }

  /**
   * 관리자용 회원 상세 조회 (기본 정보 + 전체 물품 + 신고당한 내역)
   */
  @Transactional(readOnly = true)
  public AdminResponse getMemberDetailForAdmin(AdminRequest request) {
    log.debug("회원 상세 조회: memberId={}", request.getMemberId());

    Member member = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> {
          log.error("회원을 찾을 수 없음: memberId={}", request.getMemberId());
          return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        });

    List<Item> items = itemRepository.findByMemberAndIsDeletedFalseOrderByCreatedDateDesc(member);
    List<MemberReport> memberReports = memberReportRepository.findByTargetMemberOrderByCreatedDateDesc(member);

    log.info("회원 상세 조회 완료: memberId={}, itemCount={}, reportCount={}",
        member.getMemberId(), items.size(), memberReports.size());

    return AdminResponse.builder()
        .memberDetail(AdminResponse.AdminMemberDetailDto.builder()
            .member(member)
            .items(items)
            .memberReports(memberReports)
            .reportCount((long) memberReports.size())
            .build())
        .build();
  }

  /**
   * 관리자용 회원 상태 변경
   */
  @Transactional
  public AdminResponse updateMemberStatusForAdmin(AdminRequest request) {
    log.debug("회원 상태 변경: memberId={}, accountStatus={}", request.getMemberId(), request.getAccountStatus());

    Member member = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> {
          log.error("회원을 찾을 수 없음: memberId={}", request.getMemberId());
          return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        });

    member.setAccountStatus(request.getAccountStatus());
    memberRepository.save(member);

    log.info("회원 상태 변경 완료: memberId={}, accountStatus={}", member.getMemberId(), member.getAccountStatus());

    return AdminResponse.builder()
        .member(member)
        .build();
  }
}
