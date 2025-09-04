package com.romrom.application.init;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.logServerInitDuration;

import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.Role;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RomRomInitiation implements ApplicationRunner {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	
	@Value("${admin.username}")
	private String adminUsername;
	
	@Value("${admin.password}")
	private String adminPassword;

	@Override
	public void run(ApplicationArguments args) throws Exception {
		lineLog("SERVER START");
		lineLog("데이터 초기화 시작");
		LocalDateTime startTime = LocalDateTime.now();
		// Flyway Migration 자동 진행

		// Admin 계정 초기화
		initializeAdminAccount();

		logServerInitDuration(startTime);
		log.info("서버 데이터 초기화 및 업데이트 완료");
	}

	private void initializeAdminAccount() {
		Optional<Member> existingAdmin = memberRepository.findByEmail(adminUsername);
		
		if (existingAdmin.isPresent()) {
			log.info("관리자 계정이 이미 존재합니다: {}", adminUsername);
			return;
		}

		// Admin 계정 생성
		Member adminMember = Member.builder()
			.email(adminUsername)  // "kimchi"
			.socialPlatform(SocialPlatform.ADMIN)
			.role(Role.ROLE_ADMIN)
			.accountStatus(AccountStatus.ACTIVE_ACCOUNT)
			.password(passwordEncoder.encode(adminPassword))  // 암호화된 비밀번호
			.isFirstLogin(false)
			.isItemCategorySaved(true)
			.isFirstItemPosted(false)
			.isMemberLocationSaved(false)
			.isRequiredTermsAgreed(true)
			.isMarketingInfoAgreed(false)
			.isDeleted(false)
			.build();

		memberRepository.save(adminMember);
		log.info("관리자 계정이 생성되었습니다: {}", adminUsername);
	}

}