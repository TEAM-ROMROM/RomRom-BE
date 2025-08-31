package com.romrom.web.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AdminAuthService {
    
    @Value("${admin.username}")
    private String adminUsername;
    
    @Value("${admin.password}")
    private String adminPassword;
    private final int maxSessions = 10;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, String> adminAccounts = new HashMap<>();
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // 비밀번호 암호화하여 저장
        adminAccounts.put(adminUsername, passwordEncoder.encode(adminPassword));
        log.info("관리자 계정 초기화 완료: {}", adminUsername);
    }
    
    /**
     * 관리자 인증
     */
    public boolean authenticate(String username, String password) {
        String encodedPassword = adminAccounts.get(username);
        if (encodedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(password, encodedPassword);
    }
    
    /**
     * 세션 추가 (최대 세션 체크)
     */
    public boolean addSession(String sessionId, String username) {
        if (activeSessions.size() >= maxSessions) {
            log.warn("최대 세션 수 초과: 현재 {}/{}", activeSessions.size(), maxSessions);
            return false;
        }
        activeSessions.put(sessionId, username);
        log.info("세션 추가: {} (현재 세션 수: {})", username, activeSessions.size());
        return true;
    }
    
    /**
     * 세션 제거
     */
    public void removeSession(String sessionId) {
        String username = activeSessions.remove(sessionId);
        if (username != null) {
            log.info("세션 제거: {} (현재 세션 수: {})", username, activeSessions.size());
        }
    }
    
    /**
     * 세션 체크
     */
    public boolean isValidSession(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }
    
    /**
     * 현재 활성 세션 수
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}