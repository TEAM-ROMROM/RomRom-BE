package com.romrom.web.controller.view;

import com.romrom.item.service.ItemService;
import com.romrom.member.service.MemberService;
import com.romrom.application.service.AdminAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
@Slf4j
public class AdminPageController {

    private final MemberService memberService;
    private final ItemService itemService;
    private final AdminAuthService adminAuthService;

    @GetMapping("/login")
    @LogMonitor
    public String loginPage() {
        return "admin/login";
    }

    @GetMapping("/logout")
    @LogMonitor
    public String logout(@CookieValue(value = "refreshToken", required = false) String refreshToken,
                        HttpServletResponse response,
                        RedirectAttributes redirectAttributes) {

        if (refreshToken != null) {
            adminAuthService.logout(refreshToken);
        }

        // 쿠키 삭제
        Cookie accessTokenCookie = new Cookie("accessToken", null);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(0);
        response.addCookie(accessTokenCookie);

        Cookie refreshTokenCookie = new Cookie("refreshToken", null);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(0);
        response.addCookie(refreshTokenCookie);

        Cookie authStatusCookie = new Cookie("authStatus", null);
        authStatusCookie.setPath("/");
        authStatusCookie.setMaxAge(0);
        response.addCookie(authStatusCookie);

        log.info("관리자 로그아웃 완료");
        redirectAttributes.addAttribute("logout", true);
        return "redirect:/admin/login";
    }

    @GetMapping("")
    @LogMonitor
    public String dashboard(Model model) {
        model.addAttribute("pageTitle", "대시보드");
        model.addAttribute("currentMenu", "dashboard");
        model.addAttribute("totalMembers", memberService.countActiveMembers());
        model.addAttribute("totalItems", itemService.countActiveItems());
        return "admin/dashboard";
    }

    @GetMapping("/members")
    @LogMonitor
    public String members(Model model) {
        model.addAttribute("pageTitle", "회원 관리");
        model.addAttribute("currentMenu", "members");
        return "admin/members";
    }

    @GetMapping("/items")
    @LogMonitor
    public String items(Model model) {
        model.addAttribute("pageTitle", "물품 관리");
        model.addAttribute("currentMenu", "items");
        return "admin/items";
    }

    @GetMapping("/reports")
    @LogMonitor
    public String reports(Model model) {
        model.addAttribute("pageTitle", "신고 관리");
        model.addAttribute("currentMenu", "reports");
        return "admin/reports";
    }

    @GetMapping("/settings")
    @LogMonitor
    public String settings(Model model) {
        model.addAttribute("pageTitle", "설정");
        model.addAttribute("currentMenu", "settings");
        return "admin/settings";
    }
}
