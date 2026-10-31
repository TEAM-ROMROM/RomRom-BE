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
        // 테마 전환 알림 (대시보드 차트 색 다시 그리기, #827)
        document.dispatchEvent(new CustomEvent('admin-theme-change', { detail: { theme } }));
    }
};

/**
 * enum 코드 > 표시 이름 (원본: 서버 AdminEnumLabels, layout.html 주입)
 */
const AdminLabels = {
    label: function(enumName, code) {
        if (code === null || code === undefined || code === '') return '-';
        const labelByCode = (window.AdminLabelSource || {})[enumName] || {};
        return labelByCode[code] || code;
    },
    // select 옵션 HTML: 전체 + enum 선언 순서
    options: function(enumName, allLabel = '전체') {
        const labelByCode = (window.AdminLabelSource || {})[enumName] || {};
        const allOption = allLabel ? `<option value="">${allLabel}</option>` : '';
        return allOption + Object.entries(labelByCode)
            .map(([code, label]) => `<option value="${code}">${escapeHtml(label)}</option>`).join('');
    }
};

/**
 * 상태 배지 공통: enum 코드 > { label (서버 표시 이름), cls (DaisyUI 색) }
 */
const AdminBadges = (function () {
    // enum별 상태 색 (없는 코드는 badge-ghost)
    const BADGE_CLASS_BY_ENUM_NAME = {
        TradeStatus: { PENDING: 'badge-warning', CHATTING: 'badge-info', TRADE_COMPLETE_REQUESTED: 'badge-primary', TRADED: 'badge-success', CANCELED: 'badge-neutral' },
        AccountStatus: { ACTIVE_ACCOUNT: 'badge-success', SUSPENDED_ACCOUNT: 'badge-error', DELETE_ACCOUNT: 'badge-neutral', TEST_ACCOUNT: 'badge-warning' },
        ReportStatus: { PENDING: 'badge-warning', PROCESSING: 'badge-info', COMPLETED: 'badge-success', REJECTED: 'badge-neutral' },
        ItemStatus: { AVAILABLE: 'badge-success', EXCHANGED: 'badge-neutral' }
    };
    function statusMap(enumName) {
        const labelByCode = (window.AdminLabelSource || {})[enumName] || {};
        const classByCode = BADGE_CLASS_BY_ENUM_NAME[enumName] || {};
        const badgeInfoByCode = {};
        Object.keys(labelByCode).forEach(code => {
            badgeInfoByCode[code] = { label: labelByCode[code], cls: classByCode[code] || 'badge-ghost' };
        });
        return badgeInfoByCode;
    }
    function html(enumName, code) {
        if (!code) return '<span class="badge badge-sm badge-ghost">-</span>';
        const classByCode = BADGE_CLASS_BY_ENUM_NAME[enumName] || {};
        return `<span class="badge badge-sm ${classByCode[code] || 'badge-ghost'}">${escapeHtml(AdminLabels.label(enumName, code))}</span>`;
    }
    return { statusMap, html };
})();

/**
 * 정지 기한 규칙: 영구정지 저장값, 영구 판정 (서버 AccountStatus.PERMANENT_SUSPENSION_THRESHOLD 와 같은 기준)
 */
const AdminSuspension = {
    PERMANENT_UNTIL_VALUE: '2100-01-01T00:00',
    PERMANENT_THRESHOLD: '2100-01-01',
    isPermanent: function(suspendedUntil) {
        return !!suspendedUntil && String(suspendedUntil) >= AdminSuspension.PERMANENT_THRESHOLD;
    },
    formatUntil: function(suspendedUntil) {
        if (!suspendedUntil) return '-';
        return AdminSuspension.isPermanent(suspendedUntil) ? '영구' : TimeUtils.formatDateTime(suspendedUntil);
    }
};

/**
 * 관리자 API 호출: POST multipart > 실패 시 서버 오류 메시지(errorMessage)로 예외 > 성공 시 JSON
 */
const AdminApi = {
    call: async function(url, params = {}) {
        const response = await adminFetch.post(url, params);
        if (!response.ok) {
            let serverErrorMessage = `요청 실패 (${response.status})`;
            try {
                const errorBody = await response.json();
                if (errorBody && errorBody.errorMessage) serverErrorMessage = errorBody.errorMessage;
            } catch (ignored) { /* 본문 없는 오류 */ }
            throw new Error(serverErrorMessage);
        }
        const responseText = await response.text();
        return responseText ? JSON.parse(responseText) : {};
    },
    // 실패 알림 (서버 사유 포함)
    alertError: function(error, actionLabel = '처리') {
        console.error(`[admin] ${actionLabel} failed`, error);
        alert(`${actionLabel}에 실패했습니다.\n${error && error.message ? error.message : ''}`);
    }
};

/**
 * 페이지 번호 버튼 (0부터 시작): 이전 · 번호 최대 7개 · 다음
 */
const AdminPager = {
    render: function(containerElement, currentPage, totalPages, onPageSelected) {
        if (!containerElement) return;
        if (!totalPages || totalPages <= 1) { containerElement.innerHTML = ''; return; }
        const visiblePageCount = 7;
        const firstVisiblePage = Math.max(0, Math.min(currentPage - 3, totalPages - visiblePageCount));
        const lastVisiblePage = Math.min(totalPages - 1, firstVisiblePage + visiblePageCount - 1);
        const pageButtons = [];
        pageButtons.push(`<button class="join-item btn btn-sm" data-page="${currentPage - 1}" ${currentPage === 0 ? 'disabled' : ''}>‹</button>`);
        for (let pageIndex = firstVisiblePage; pageIndex <= lastVisiblePage; pageIndex++) {
            pageButtons.push(`<button class="join-item btn btn-sm ${pageIndex === currentPage ? 'btn-active' : ''}" data-page="${pageIndex}">${pageIndex + 1}</button>`);
        }
        pageButtons.push(`<button class="join-item btn btn-sm" data-page="${currentPage + 1}" ${currentPage >= totalPages - 1 ? 'disabled' : ''}>›</button>`);
        containerElement.innerHTML = `<div class="join">${pageButtons.join('')}</div>`;
        containerElement.querySelectorAll('button[data-page]').forEach(pageButton => {
            pageButton.addEventListener('click', () => onPageSelected(Number(pageButton.dataset.page)));
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
window.AdminLabels = AdminLabels;
window.AdminBadges = AdminBadges;
window.AdminSuspension = AdminSuspension;
window.AdminApi = AdminApi;
window.AdminPager = AdminPager;
