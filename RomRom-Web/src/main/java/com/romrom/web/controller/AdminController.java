package com.romrom.web.controller;

import com.romrom.member.service.MemberService;
import com.romrom.item.service.ItemService;
import com.romrom.web.service.AdminAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
@Slf4j
public class AdminController {
    
    private final MemberService memberService;
    private final ItemService itemService;
    private final AdminAuthService adminAuthService;
    
    @GetMapping("/login")
    public String loginPage() {
        return "admin/login";
    }
    
    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        
        // 인증 확인
        if (!adminAuthService.authenticate(username, password)) {
            redirectAttributes.addAttribute("error", true);
            return "redirect:/admin/login";
        }
        
        // 세션 수 체크
        String sessionId = session.getId();
        if (!adminAuthService.addSession(sessionId, username)) {
            redirectAttributes.addAttribute("maxSessions", true);
            return "redirect:/admin/login";
        }
        
        // 세션에 관리자 정보 저장
        session.setAttribute("adminUser", username);
        session.setMaxInactiveInterval(3600); // 1시간
        
        log.info("관리자 로그인 성공: {}", username);
        return "redirect:/admin";
    }
    
    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        String username = (String) session.getAttribute("adminUser");
        if (username != null) {
            adminAuthService.removeSession(session.getId());
            session.invalidate();
            log.info("관리자 로그아웃: {}", username);
        }
        redirectAttributes.addAttribute("logout", true);
        return "redirect:/admin/login";
    }
    
    @GetMapping("")
    public String dashboard(Model model, HttpSession session) {
        // 세션 체크
        if (session.getAttribute("adminUser") == null) {
            return "redirect:/admin/login";
        }
        model.addAttribute("pageTitle", "대시보드");
        // 통계 데이터 추가
        model.addAttribute("totalMembers", memberService.countActiveMembers());
        model.addAttribute("totalItems", itemService.countActiveItems());
        
        return "admin/dashboard";
    }
    
    @GetMapping("/members")
    public String members(Model model, HttpSession session) {
        // 세션 체크
        if (session.getAttribute("adminUser") == null) {
            return "redirect:/admin/login";
        }
        model.addAttribute("pageTitle", "회원 관리");
        model.addAttribute("members", memberService.getAllMembers());
        return "admin/members";
    }
    
    @GetMapping("/items")
    public String items(Model model, HttpSession session) {
        // 세션 체크
        if (session.getAttribute("adminUser") == null) {
            return "redirect:/admin/login";
        }
        model.addAttribute("pageTitle", "물품 관리");
        model.addAttribute("items", itemService.getAllItems());
        return "admin/items";
    }
    
    @GetMapping("/reports")
    public String reports(Model model, HttpSession session) {
        // 세션 체크
        if (session.getAttribute("adminUser") == null) {
            return "redirect:/admin/login";
        }
        model.addAttribute("pageTitle", "신고 관리");
        return "admin/reports";
    }
    
    @GetMapping("/settings")
    public String settings(Model model, HttpSession session) {
        // 세션 체크
        if (session.getAttribute("adminUser") == null) {
            return "redirect:/admin/login";
        }
        model.addAttribute("pageTitle", "설정");
        return "admin/settings";
    }
}