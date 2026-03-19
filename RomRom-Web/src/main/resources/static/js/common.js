/**
 * RomRom Admin 공통 JavaScript
 */

// 관리자 인증
const AdminAuth = {
    checkToken: function() {
        const accessToken = localStorage.getItem('adminAccessToken');
        return accessToken && accessToken.trim() !== '';
    },

    getAccessToken: function() {
        return localStorage.getItem('adminAccessToken');
    },

    redirectToLogin: function(error = '') {
        localStorage.removeItem('adminAccessToken');
        const loginUrl = error ? `/admin/login?error=${error}` : '/admin/login';
        if (window.location.pathname !== '/admin/login') {
            window.location.href = loginUrl;
        }
    },

    logout: async function() {
        try {
            await adminFetch.post('/api/admin/logout');
        } catch (error) {
            console.error('로그아웃 요청 실패:', error);
        } finally {
            localStorage.removeItem('adminAccessToken');
            window.location.href = '/admin/login';
        }
    },

    handleApiResponse: function(response) {
        if (response.status === 401 || response.status === 403) {
            AdminAuth.redirectToLogin();
            return false;
        }
        return true;
    }
};

// fetch API 래퍼
const adminFetch = async function(url, options = {}) {
    const defaultHeaders = {};
    const accessToken = AdminAuth.getAccessToken();
    if (accessToken) {
        defaultHeaders['Authorization'] = `Bearer ${accessToken}`;
    }

    // FormData가 아닌 경우에만 Content-Type을 application/json으로 설정
    // FormData는 브라우저가 boundary 포함된 multipart/form-data 헤더를 자동 설정
    if (!(options.body instanceof FormData)) {
        defaultHeaders['Content-Type'] = 'application/json';
    }

    const response = await fetch(url, {
        ...options,
        headers: { ...defaultHeaders, ...options.headers },
        credentials: 'include',
    });

    if (!AdminAuth.handleApiResponse(response)) {
        throw new Error('인증 실패');
    }
    return response;
};

/**
 * Admin API 표준 호출 (POST + multipart/form-data)
 * @param {string} url - API 엔드포인트
 * @param {Object} params - 전송할 파라미터 (key-value)
 * @returns {Promise<Response>}
 */
adminFetch.post = async function(url, params = {}) {
    const formData = new FormData();
    for (const [paramKey, paramValue] of Object.entries(params)) {
        if (paramValue !== null && paramValue !== undefined && paramValue !== '') {
            formData.append(paramKey, paramValue);
        }
    }
    return adminFetch(url, { method: 'POST', body: formData });
};

// 시간 유틸리티
const TimeUtils = {
    getRelativeTime: function(dateString) {
        const now = new Date();
        const targetDate = new Date(dateString);
        const diffInSeconds = Math.floor((now - targetDate) / 1000);

        if (diffInSeconds < 60) return '방금전';
        const diffInMinutes = Math.floor(diffInSeconds / 60);
        if (diffInMinutes < 60) return `${diffInMinutes}분전`;
        const diffInHours = Math.floor(diffInMinutes / 60);
        if (diffInHours < 24) return `${diffInHours}시간전`;
        const diffInDays = Math.floor(diffInHours / 24);
        if (diffInDays < 30) return `${diffInDays}일전`;
        const diffInMonths = Math.floor(diffInDays / 30);
        if (diffInMonths < 12) return `${diffInMonths}달전`;
        return `${Math.floor(diffInMonths / 12)}년전`;
    },

    formatDate: function(dateString) {
        const date = new Date(dateString);
        return date.toISOString().split('T')[0];
    },

    formatDateTime: function(dateString) {
        const date = new Date(dateString);
        return date.toLocaleString('ko-KR', {
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit'
        });
    }
};

// HTML 이스케이프 (XSS 방지)
function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// 숫자 유틸리티
const NumberUtils = {
    formatNumber: function(number) {
        return number ? number.toLocaleString() : '0';
    },
    formatPrice: function(price) {
        return price ? `₩${price.toLocaleString()}` : '가격 없음';
    }
};

// 테마 유틸리티
const ThemeUtils = {
    init: function() {
        const saved = localStorage.getItem('adminTheme');
        const theme = saved || 'light';
        document.documentElement.setAttribute('data-theme', theme);

        const toggle = document.getElementById('themeToggle');
        if (toggle) {
            toggle.checked = theme === 'dark';
            toggle.addEventListener('change', function() {
                const newTheme = this.checked ? 'dark' : 'light';
                ThemeUtils.setTheme(newTheme);
            });
        }
    },

    setTheme: function(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('adminTheme', theme);
        // 모든 테마 토글 동기화
        const isDark = theme === 'dark';
        ['themeToggle', 'settingsThemeToggle'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.checked = isDark;
        });
    }
};

// 초기화
document.addEventListener('DOMContentLoaded', function() {
    ThemeUtils.init();
});

// 전역 노출
window.AdminAuth = AdminAuth;
window.adminFetch = adminFetch;
window.TimeUtils = TimeUtils;
window.NumberUtils = NumberUtils;
window.ThemeUtils = ThemeUtils;
window.escapeHtml = escapeHtml;
