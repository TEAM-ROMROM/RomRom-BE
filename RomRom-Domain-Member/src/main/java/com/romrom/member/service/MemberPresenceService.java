package com.romrom.member.service;

import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberPresenceService {
    private final MemberRepository memberRepository;
    
    // DB 업데이트 최소 간격
    private static final int WRITE_MIN_INTERVAL_SECONDS = 60;

    @Transactional
    public void heartbeat(Member member) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastUpdate = member.getLastActiveAt();

        // 마지막 갱신 시각이 없거나, 설정한 간격보다 오래됐을 때만 DB 업데이트
        if (lastUpdate == null || lastUpdate.isBefore(now.minusSeconds(WRITE_MIN_INTERVAL_SECONDS))) {
            memberRepository.updateLastActiveAt(member.getMemberId(), now);
        }
    }
}